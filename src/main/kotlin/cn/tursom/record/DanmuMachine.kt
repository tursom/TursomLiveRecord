package cn.tursom.record

import cn.tursom.core.ThreadLocalSimpleDateFormat
import cn.tursom.core.seconds
import cn.tursom.log.setLogLevel
import cn.tursom.mail.GroupEmailData
import cn.tursom.record.config.DanmuConfig
import cn.tursom.record.util.OnCloseCallbackOutputStream
import cn.tursom.yaml.Yaml
import com.google.protobuf.TextFormat
import kotlinx.coroutines.delay
import org.slf4j.event.Level
import java.io.File
import javax.activation.FileDataSource

private val dateFormat = ThreadLocalSimpleDateFormat("YYYY-MM-dd HH-mm")

suspend fun main(args: Array<String>) {
  setDefaultTextFormatPrinter(TextFormat.printer().escapingNonAscii(false))
  setLogLevel(Level.ERROR)
  val config = run {
    val configFilePath = if (args.isNotEmpty()) {
      args[0]
    } else {
      "danmu.yml"
    }
    Yaml.parse<DanmuConfig>(File(configFilePath).readText())!!
  }

  config.room.forEach { (roomId, file) ->
    val baseEmailData = GroupEmailData(
      subject = "${file}直播记录",
      host = config.mail.host,
      port = config.mail.port,
      name = config.mail.user,
      password = config.mail.password,
      from = config.mail.from ?: config.mail.user,
      to = config.mail.target.asSequence()
        .filter { it.roomList == null || it.roomList.contains(roomId.toString()) }
        .map { it.mail }
        .toList(),
    )

    recordDanmu(roomId) {
      val outFile = File("$file-${dateFormat.now()}.rec")
      OnCloseCallbackOutputStream(outFile.outputStream()) {
        baseEmailData.copy(
          text = "${dateFormat.now()} 直播记录",
          attachment = listOf(FileDataSource(outFile))
        ).send()
      }
    }
  }

  while (true) {
    delay(1.seconds().toMillis())
  }
}
