package cn.tursom.record

import cn.tursom.log.setLogLevel
import cn.tursom.ws.BiliWSClient
import com.google.protobuf.TextFormat
import org.slf4j.event.Level

suspend fun main() {
  setDefaultTextFormatPrinter(TextFormat.printer().escapingNonAscii(false))
  setLogLevel(Level.ERROR)

  setLogLevel(Level.ERROR, "cn.tursom")
  val roomId = 917818

  val wsClient = BiliWSClient(roomId)
  wsClient.addDanmuListener {
    printDanmu(it.toProtobuf())
  }

  wsClient.connect()
}
