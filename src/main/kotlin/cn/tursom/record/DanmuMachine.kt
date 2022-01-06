package cn.tursom.record

import cn.tursom.core.ThreadLocalSimpleDateFormat
import cn.tursom.core.seconds
import cn.tursom.im.connect
import cn.tursom.log.setLogLevel
import cn.tursom.record.context.GlobalContext
import cn.tursom.record.entity.User
import cn.tursom.record.entity.UserRoom
import cn.tursom.record.util.OnCloseCallbackOutputStream
import cn.tursom.ws.CmdEnum
import com.google.protobuf.TextFormat
import kotlinx.coroutines.delay
import org.slf4j.event.Level
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.activation.FileDataSource

private val dateFormat = ThreadLocalSimpleDateFormat("YYYY-MM-dd HH-mm")

suspend fun main(args: Array<String>) {
  setDefaultTextFormatPrinter(TextFormat.printer().escapingNonAscii(false))
  setLogLevel(Level.ERROR)

  val globalContext = GlobalContext(args)
  globalContext.configContext.config.im?.let { im ->
    val imClient = connect(im.server, im.token)
    imClient.handler.broadcast.registerListenLiveRoomHandler { client, receiveMsg, listenLiveRoom ->
      listenLiveRoom
    }
    imClient.listenBroadcast(im.channel ?: 12345) { client, receiveMsg ->
      receiveMsg
    }
  }

  val userRoomMap = globalContext.dbContext.listUserRoom()
    .groupBy {
      it.uid
    }
    .mapValues {
      it.value.map(UserRoom::roomId)
    }
  globalContext.dbContext.listRoom().forEach { (roomId, file) ->
    val baseEmailData = globalContext.configContext.baseEmailData.copy(
      to = globalContext.dbContext.listUsers().asSequence()
        .filter {
          val userRooms = userRoomMap[it.uid]
          userRooms != null && userRooms.contains(roomId)
        }.map(User::uid).toList(),
    )

    val biliWSClient = recordDanmu(roomId) {
      val outFile = File("$file-${dateFormat.now()}.rec")
      OnCloseCallbackOutputStream(outFile.outputStream()) {
        baseEmailData.copy(
          text = "${dateFormat.now()} 直播记录",
          attachment = listOf(FileDataSource(outFile))
        ).send()
      }
    }

    val livingState = AtomicBoolean(biliWSClient.living)
    biliWSClient.addLivingListener {
      if (livingState.compareAndSet(false, true)) {
        baseEmailData.copy(
          subject = "${file}开锅了！",
          text = "https://live.bilibili.com/$roomId",
        ).send()
      }
    }
    biliWSClient.addCmdListener(CmdEnum.PREPARING) {
      if (livingState.compareAndSet(true, false)) {
        baseEmailData.copy(
          subject = "${file}下锅了！",
          text = "https://live.bilibili.com/$roomId",
        ).send()
      }
    }
  }

  while (true) {
    delay(1.seconds().toMillis())
  }
}
