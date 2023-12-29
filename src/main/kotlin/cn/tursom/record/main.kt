@file:OptIn(DelicateCoroutinesApi::class)

package cn.tursom.record

import cn.tursom.core.util.ThreadLocalSimpleDateFormat
import cn.tursom.core.buffer.ByteBuffer
import cn.tursom.core.coroutine.bufferTicker
import cn.tursom.core.util.seconds
import cn.tursom.log.impl.Slf4jImpl
import cn.tursom.record.provider.BilibiliLiveProvider
import cn.tursom.record.saver.FileLiveSaver
import cn.tursom.ws.BiliWSClient
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private val logger = Slf4jImpl.getLogger()
private val dateFormat = ThreadLocalSimpleDateFormat("YYYY-MM-dd HH-mm-ss")
private val retryDelaySecondsList = intArrayOf(5, 5, 10, 10, 30, 30, 60, 60, 60, 300)
private const val fileMaxSize = 4L * 1024 * 1024 * 1024

private val recordRooms = listOf(
  1138 to "乌拉录播",
  23018529 to "盐咪yami录播",
  917818 to "tursom录播",
)

suspend fun startRecordOnce(
  roomId: Int,
  title: String,
  onException: suspend (Exception) -> Unit,
) {
  val dataChannel = Channel<ByteBuffer>(32)
  var liveSaver: FileLiveSaver? = null
  val liveProvider = BilibiliLiveProvider(roomId, dataChannel)
  liveProvider.onException = {
    liveProvider.close()
    liveSaver?.close()
    onException(it)
  }
  try {
    liveProvider.startRecord()
    liveSaver = FileLiveSaver("$title-${dateFormat.now()}.flv", dataChannel, fileMaxSize)
  } catch (e: Exception) {
    liveProvider.close()
    liveSaver?.close()
    throw e
  }
}

private data class StartLiveMsg(
  val roomId: Int,
  val title: String,
  val client: BiliWSClient,
)

fun main() = runBlocking {
  val startRecordChannel = Channel<StartLiveMsg>(16)
  val recordTicker = bufferTicker(5.seconds().toMillis(), 3)

  launch {
    val recordingRooms = ConcurrentHashMap<Int, String>()

    suspend fun connect(roomId: Int, title: String, wsClient: BiliWSClient) {
      val danmuOutputStream = File("$title-${dateFormat.now()}.danmu").outputStream().buffered()
      try {
        recordTicker.receive()
        var onException: (suspend (Exception) -> Unit)? = null
        onException = {
          launch reconnect@{
            if (wsClient.living) {
              val retryDelaySecondsIterator = retryDelaySecondsList.iterator()
              var retryDelaySeconds = retryDelaySecondsIterator.nextInt()
              while (wsClient.living) {
                delay(retryDelaySeconds.seconds().toMillis())
                if (retryDelaySecondsIterator.hasNext()) {
                  retryDelaySeconds = retryDelaySecondsIterator.nextInt()
                }
                try {
                  startRecordOnce(roomId, title, onException!!)
                  return@reconnect
                } catch (_: Exception) {
                }
              }
            }
            @Suppress("BlockingMethodInNonBlockingContext")
            danmuOutputStream.close()
            recordingRooms.remove(roomId)
          }
        }
        startRecordOnce(roomId, title, onException)
        recordingRooms[roomId] = title
      } catch (e: Exception) {
        logger.warn("an exception caused on connect to room", e)
      }
    }

    while (true) {
      val (roomId, title, wsClient) = startRecordChannel.receive()
      launch connect@{
        if (roomId !in recordingRooms.keys) {
          connect(roomId, title, wsClient)
        } else repeat(3) {
          delay(5.seconds().toMillis())
          if (roomId !in recordingRooms.keys) {
            connect(roomId, title, wsClient)
            return@connect
          }
        }
      }
    }
  }

  recordRooms.forEach { (roomId, title) ->
    val wsClient = BiliWSClient(roomId)
    val startLiveMsg = StartLiveMsg(roomId, title, wsClient)
    startRecordChannel.send(startLiveMsg)
    wsClient.addLivingListener {
      launch {
        startRecordChannel.send(startLiveMsg)
      }
    }
  }
}
