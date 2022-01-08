package cn.tursom.record

import cn.tursom.core.ThreadLocalSimpleDateFormat
import cn.tursom.core.seconds
import cn.tursom.log.setLogLevel
import cn.tursom.mail.GroupEmailData
import cn.tursom.record.context.GlobalContext
import cn.tursom.record.im.handler.ImRequestHandler
import cn.tursom.record.util.OnCloseCallbackOutputStream
import cn.tursom.ws.CmdEnum
import com.google.protobuf.TextFormat
import kotlinx.coroutines.delay
import org.slf4j.event.Level
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.activation.FileDataSource

private val dateFormat = ThreadLocalSimpleDateFormat("YYYY-MM-dd HH-mm")

fun sendLiveStartMail(globalContext: GlobalContext, baseEmailData: GroupEmailData, liver: String, roomId: Int) {
  globalContext.mailContext.sendMailBlocking(baseEmailData.copy(
    subject = "${liver}开锅了！",
    to = globalContext.dbContext.listListenRoomUsers(roomId),
    text = "https://live.bilibili.com/$roomId",
  ))
}

fun sendLiveStopMail(globalContext: GlobalContext, baseEmailData: GroupEmailData, liver: String, roomId: Int) {
  globalContext.mailContext.sendMailBlocking(baseEmailData.copy(
    subject = "${liver}下锅了！",
    to = globalContext.dbContext.listListenRoomUsers(roomId),
    text = "https://live.bilibili.com/$roomId",
  ))
}

suspend fun listenRoom(globalContext: GlobalContext, roomId: Int, liver: String) {
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

suspend fun main(args: Array<String>) {
  setDefaultTextFormatPrinter(TextFormat.printer().escapingNonAscii(false))
  setLogLevel(Level.ERROR)

  val globalContext = GlobalContext(args)
  globalContext.imContext?.let { imContext ->
    val imClient = imContext.im
    imClient.listenBroadcast(imContext.imConfig.channel ?: 12345)
    imClient.handler.broadcast.registerHandlerObject(ImRequestHandler(globalContext))
  }

  globalContext.dbContext.listRoom().forEach { (roomId, liver) ->
    listenRoom(globalContext, roomId, liver)
  }

  while (true) {
    delay(1.seconds().toMillis())
  }
}
