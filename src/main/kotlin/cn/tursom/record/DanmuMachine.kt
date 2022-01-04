package cn.tursom.record

import cn.tursom.core.ThreadLocalSimpleDateFormat
import cn.tursom.core.seconds
import cn.tursom.database.ktorm.AutoTable
import cn.tursom.database.ktorm.from
import cn.tursom.database.ktorm.select
import cn.tursom.log.setLogLevel
import cn.tursom.record.config.DanmuConfig
import cn.tursom.record.entity.Room
import cn.tursom.record.entity.UserRoom
import cn.tursom.yaml.Yaml
import com.google.protobuf.TextFormat
import kotlinx.coroutines.delay
import org.ktorm.database.Database
import org.ktorm.dsl.forEach
import org.slf4j.event.Level
import java.io.File

private val dateFormat = ThreadLocalSimpleDateFormat("YYYY-MM-dd HH-mm")

suspend fun main(args: Array<String>) {
  setDefaultTextFormatPrinter(TextFormat.printer().escapingNonAscii(false))
  setLogLevel(Level.ERROR)

  val database = Database.connect(
    "jdbc:sqlite:danmu.db",
    "org.sqlite.JDBC"
  )
  database.useConnection { conn ->
    val statement = conn.createStatement()
    statement.executeUpdate("create table if not exists room(" +
      "room_id int primary key not null," +
      "liver text" +
      ")")
    statement.executeUpdate("create table if not exists user(" +
      "uid text primary key not null," +
      "mail text" +
      ")")
    statement.executeUpdate("create table if not exists user_room(" +
      "uid text not null," +
      "room_id text not null," +
      "unique(uid,room_id)" +
      ")")
    statement.close()
  }

  val config = run {
    val configFilePath = if (args.isNotEmpty()) {
      args[0]
    } else {
      "danmu.yml"
    }
    Yaml.parse<DanmuConfig>(File(configFilePath).readText())!!
  }

//  config.im?.let { im ->
//    val imClient = connect(im.server, im.token)
//    imClient.listenBroadcast(123) { client, receiveMsg ->
//    }
//  }

  database.from<Room>().select()
    .forEach {
      val (roomId, file) = AutoTable[Room::class].createEntity(it)
      database.from<UserRoom>()
//        .select(UserRoom::uid)
//      val baseEmailData = GroupEmailData(
//        subject = "${file}直播记录",
//        host = config.mail.host,
//        port = config.mail.port,
//        name = config.mail.user,
//        password = config.mail.password,
//        from = config.mail.from ?: config.mail.user,
//        to = config.mail.target.asSequence()
//          .filter { it.room == null || it.room.contains(roomId.toString()) }
//          .map { it.mail }
//          .toList(),
//      )
//
//      val biliWSClient = recordDanmu(roomId) {
//        val outFile = File("$file-${dateFormat.now()}.rec")
//        OnCloseCallbackOutputStream(outFile.outputStream()) {
//          baseEmailData.copy(
//            text = "${dateFormat.now()} 直播记录",
//            attachment = listOf(FileDataSource(outFile))
//          ).send()
//        }
//      }
//
//      val livingState = AtomicBoolean(biliWSClient.living)
//      biliWSClient.addLivingListener {
//        if (livingState.compareAndSet(false, true)) {
//          baseEmailData.copy(
//            subject = "${file}开锅了！",
//            text = "https://live.bilibili.com/$roomId",
//          ).send()
//        }
//      }
//      biliWSClient.addCmdListener(CmdEnum.PREPARING) {
//        if (livingState.compareAndSet(true, false)) {
//          baseEmailData.copy(
//            subject = "${file}下锅了！",
//            text = "https://live.bilibili.com/$roomId",
//          ).send()
//        }
//      }
    }

  while (true) {
    delay(1.seconds().toMillis())
  }
}
