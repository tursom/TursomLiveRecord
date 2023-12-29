package cn.tursom.record.context

import cn.tursom.core.util.ThreadLocalSimpleDateFormat
import cn.tursom.record.recordDanmu
import cn.tursom.record.sendLiveStartMail
import cn.tursom.record.sendLiveStopMail
import cn.tursom.record.util.OnCloseCallbackOutputStream
import cn.tursom.ws.CmdEnum
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.activation.FileDataSource

class RoomContextImpl(
  private val globalContext: GlobalContext,
) : RoomContext {
  companion object {
    private val dateFormat = ThreadLocalSimpleDateFormat("YYYY-MM-dd HH-mm")
  }

  override suspend fun listenRoom(roomId: Int, liver: String) {
    val baseEmailData = globalContext.configContext.baseEmailData

    val biliWSClient = recordDanmu(roomId) {
      var liveRecFile = globalContext.dbContext.getRoomRecFile(roomId)
      if (liveRecFile == null) {
        liveRecFile = "rec/$liver-${dateFormat.now()}.rec"
        globalContext.dbContext.setRoomRecFile(roomId, liveRecFile)
      }
      val outFile = File(liveRecFile)
      if (outFile.exists()) {
        outFile.renameTo(File("rec/$liver-${dateFormat.now()}.rec"))
      }

      OnCloseCallbackOutputStream(outFile.outputStream()) {
        globalContext.dbContext.setRoomRecFile(roomId, "rec/$liver-${dateFormat.now()}.rec")
        globalContext.mailContext.sendMailBlocking(baseEmailData.copy(
          text = "${dateFormat.now()} 直播记录",
          to = globalContext.dbContext.listListenRoomUsers(roomId),
          attachment = listOf(FileDataSource(outFile))
        ))
      }
    }

    val livingState = AtomicBoolean(biliWSClient.living)
    if (biliWSClient.living) {
      sendLiveStartMail(globalContext, baseEmailData, liver, roomId)
    }
    biliWSClient.addLivingListener {
      if (livingState.compareAndSet(false, true)) {
        sendLiveStartMail(globalContext, baseEmailData, liver, roomId)
      }
    }
    biliWSClient.addCmdListener(CmdEnum.PREPARING) {
      if (livingState.compareAndSet(true, false)) {
        sendLiveStopMail(globalContext, baseEmailData, liver, roomId)
      }
    }
  }
}