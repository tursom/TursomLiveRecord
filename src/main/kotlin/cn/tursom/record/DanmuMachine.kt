package cn.tursom.record

import cn.tursom.core.util.ThreadLocalSimpleDateFormat
import cn.tursom.core.util.createFolderIfNotExists
import cn.tursom.core.util.gz
import cn.tursom.core.util.seconds
import cn.tursom.im.registerHandlerObject
import cn.tursom.log.setLogLevel
import cn.tursom.mail.GroupEmailData
import cn.tursom.record.context.GlobalContext
import cn.tursom.record.im.handler.ImRequestHandler
import cn.tursom.record.util.OnCloseCallbackOutputStream
import cn.tursom.record.util.recTime
import cn.tursom.ws.CmdEnum
import com.google.protobuf.TextFormat
import kotlinx.coroutines.delay
import org.slf4j.event.Level
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.activation.FileDataSource

private val dateFormat = ThreadLocalSimpleDateFormat("YYYY-MM-dd HH-mm")

fun sendLiveStartMail(globalContext: GlobalContext, baseEmailData: GroupEmailData, liver: String, roomId: Int) {
  globalContext.mailContext.sendMailBlocking(
    baseEmailData.copy(
      subject = "${liver}开锅了！",
      to = globalContext.dbContext.listListenRoomUsersMail(roomId),
      text = "https://live.bilibili.com/$roomId",
    )
  )
}

fun sendLiveStopMail(globalContext: GlobalContext, baseEmailData: GroupEmailData, liver: String, roomId: Int) {
  globalContext.mailContext.sendMailBlocking(
    baseEmailData.copy(
      subject = "${liver}下锅了！",
      to = globalContext.dbContext.listListenRoomUsersMail(roomId),
      text = "https://live.bilibili.com/$roomId",
    )
  )
}

suspend fun listenRoom(globalContext: GlobalContext, roomId: Int, liver: String) {
  createFolderIfNotExists("rec")
  val baseEmailData = globalContext.configContext.baseEmailData

  val biliWSClient = recordDanmu(roomId) {
    var liveRecFile = globalContext.dbContext.getRoomRecFile(roomId)
    if (liveRecFile.isNullOrBlank()) {
      liveRecFile = "rec/$liver-${dateFormat.now()}.rec"
      globalContext.dbContext.setRoomRecFile(roomId, liveRecFile)
    }
    val outFile = File(liveRecFile)
    if (outFile.exists()) {
      outFile.renameTo(File("rec/$liver-${dateFormat.now()}.rec"))
    }

    OnCloseCallbackOutputStream(outFile.outputStream()) {
      val (start, end) = outFile.inputStream().recTime()
      if (start < 0) {
        return@OnCloseCallbackOutputStream
      }
      globalContext.dbContext.setRoomRecFile(roomId, "rec/$liver-${dateFormat.now()}.rec")
      globalContext.dbContext.recordLiveRecordFile(roomId, outFile.name, start, end)
      val gzOutFile = outFile.gz()
      globalContext.mailContext.sendMailBlocking(
        baseEmailData.copy(
          subject = "$liver 直播记录",
          text = "$liver ${dateFormat.now()} 直播记录",
          to = globalContext.dbContext.listListenRoomUsersMail(roomId),
          attachment = listOf(FileDataSource(gzOutFile))
        )
      ) {
        gzOutFile.delete()
      }
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

    imClient.handler.onMsgRead = { msg ->
      println("read im msg: $msg")
    }

    val imRequestHandler = ImRequestHandler(globalContext)
    imClient.handler.broadcast.registerHandlerObject(imRequestHandler)
    imClient.handler.system.registerHandlerObject(imRequestHandler)
  }

  globalContext.dbContext.listRoom().forEach { (roomId, liver) ->
    listenRoom(globalContext, roomId, liver)
  }

  while (true) {
    delay(1.seconds().toMillis())
  }
}
