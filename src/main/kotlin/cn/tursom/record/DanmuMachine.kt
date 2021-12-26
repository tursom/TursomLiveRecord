package cn.tursom.record

import cn.tursom.core.ThreadLocalSimpleDateFormat
import cn.tursom.core.seconds
import cn.tursom.log.setLogLevel
import com.google.protobuf.TextFormat
import kotlinx.coroutines.delay
import org.slf4j.event.Level
import java.io.File

private val dateFormat = ThreadLocalSimpleDateFormat("YYYY-MM-dd HH")

suspend fun main() {
  setDefaultTextFormatPrinter(TextFormat.printer().escapingNonAscii(false))
  setLogLevel(Level.ERROR)

  danmuRecordRooms.forEach { (roomId, file) ->
    recordDanmu(roomId) { File("$file-${dateFormat.now()}.rec").outputStream() }
  }

  while (true) {
    delay(1.seconds().toMillis())
  }
}
