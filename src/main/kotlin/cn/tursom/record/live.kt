package cn.tursom.record

import cn.tursom.core.ThreadLocalSimpleDateFormat
import cn.tursom.core.buffer.ByteBuffer
import cn.tursom.core.seconds
import cn.tursom.log.impl.Slf4jImpl
import cn.tursom.record.provider.BilibiliLiveProvider
import cn.tursom.record.saver.FileLiveSaver
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import java.io.IOException
import kotlin.random.Random

private val logger = Slf4jImpl.getLogger()
private val retryDelaySecondsList = intArrayOf(5, 5, 10, 10, 30, 30, 60, 60, 60)
private val dateFormat = ThreadLocalSimpleDateFormat.standard

const val fileMaxSize = 4L * 1024 * 1024 * 1024
//const val roomId = 1138
//const val title = "乌拉录播"
const val roomId = 23018529
const val title = "盐咪yami录播"

suspend fun main() {
  var dataChannel = Channel<ByteBuffer>(128)
  var liveSaver: FileLiveSaver? = null
  var liveProvider = BilibiliLiveProvider(roomId, dataChannel)
  liveProvider.onException = {
    liveProvider.finish()
    liveSaver?.finish()
    val onException = liveProvider.onException
    dataChannel = Channel(128)

    val retryDelaySecondsIterator = retryDelaySecondsList.iterator()
    var waitSeconds = retryDelaySecondsIterator.nextInt()
    while (true) {
      delay((waitSeconds.seconds().toMillis() * Random.nextDouble(0.8, 1.2)).toLong())
      if (retryDelaySecondsIterator.hasNext()) {
        waitSeconds = retryDelaySecondsIterator.nextInt()
      }
      try {
        liveProvider = BilibiliLiveProvider(roomId, dataChannel, onException)
        liveProvider.startRecord()
        liveSaver = FileLiveSaver("$title-${dateFormat.now()}.flv", dataChannel, fileMaxSize, onException)
        break
      } catch (e: IOException) {
        logger.info("room live not started")
      } catch (e: Exception) {
        logger.warn("an exception caused on reconnect to live server", e)
      }
    }
  }
  try {
    liveProvider.startRecord()
    liveSaver = FileLiveSaver("$title-${dateFormat.now()}.flv", dataChannel, fileMaxSize)
  } catch (e: Exception) {
    liveProvider.onException(e)
  }

  while (true) {
    delay(1000)
  }
}