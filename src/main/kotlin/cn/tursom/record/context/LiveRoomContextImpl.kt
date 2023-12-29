package cn.tursom.record.context

import cn.tursom.core.util.ThreadLocalSimpleDateFormat
import cn.tursom.core.coroutine.GlobalScope
import cn.tursom.log.impl.Slf4jImpl
import cn.tursom.record.recordDanmu
import cn.tursom.record.util.OnCloseCallbackOutputStream
import cn.tursom.ws.BiliWSClient
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class LiveRoomContextImpl(
  private val configContext: ConfigContext,
  private val dbContext: DBContext,
) : LiveRoomContext {
  companion object : Slf4jImpl() {
    private val dateFormat = ThreadLocalSimpleDateFormat("YYYY-MM-dd HH-mm")

    init {
      try {
        File("rec").mkdirs()
      } catch (_: Exception) {
      }
    }
  }

  private val liveMap = ConcurrentHashMap<Int, BiliWSClient>()
  private val liveFinishedChannel = Channel<Pair<Int, File>>()

  init {
    GlobalScope.launch {
      while (true) try {
        val (roomId, recFile) = liveFinishedChannel.receive()
        val liveUser = dbContext.getLiveRoomById(roomId)?.liver?.ifBlank { null }
          ?: liveMap[roomId]?.userName
          ?: continue
        configContext.baseEmailData
      } catch (e: Exception) {
        logger.error("an exception caused on handle")
      }
    }
  }

  override fun listenRoom(roomId: Int, liver: String?): BiliWSClient {
    var biliWSClient = liveMap[roomId]
    if (biliWSClient == null) synchronized(this) {
      biliWSClient = liveMap[roomId]
      if (biliWSClient != null) {
        return biliWSClient!!
      }

      biliWSClient = runBlocking {
        recordDanmu(roomId) {
          val outFile = File("rec/$roomId-${dateFormat.now()}.rec")
          OnCloseCallbackOutputStream(outFile.outputStream()) {
            runBlocking {
              liveFinishedChannel.trySend(roomId to outFile)
            }
          }
        }
      }
      dbContext.addLiveRoom(roomId, liver ?: biliWSClient?.userName)
    }

    return biliWSClient!!
  }
}