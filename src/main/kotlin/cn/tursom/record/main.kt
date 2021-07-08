package cn.tursom.record

import cn.tursom.core.final
import cn.tursom.core.toByteArray
import cn.tursom.log.impl.Slf4jImpl
import cn.tursom.ws.BiliWSClient
import com.google.protobuf.TextFormat
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.ByteOrder

private val logger = Slf4jImpl.getLogger()

fun setDefaultTextFormatPrinter(printer: TextFormat.Printer) {
  val defaultPrinter = TextFormat.Printer::class.java.getDeclaredField("DEFAULT")
  defaultPrinter.isAccessible = true
  defaultPrinter.final = false
  defaultPrinter.set(null, printer)
}

@Suppress("BlockingMethodInNonBlockingContext")
@OptIn(ExperimentalUnsignedTypes::class)
suspend fun main() {
  setDefaultTextFormatPrinter(TextFormat.printer().escapingNonAscii(false))

  val danmuChannel = Channel<Record.RecordMsg>(128)

  val os = File("danmu.rec").outputStream()
  val roomId = 4767523
  val biliWSClient = BiliWSClient(roomId)
  biliWSClient.addDanmuListener {
    runBlocking {
      danmuChannel.send(Record.RecordMsg.newBuilder()
        .setDanmu(Record.DanmuRecord.newBuilder()
          .setRoomId(roomId)
          .setDanmu(it.toProtobuf()))
        .build())
    }
  }

  GlobalScope.launch {
    while (true) {
      val danmuInfo = danmuChannel.receive()
      val bytes = danmuInfo.toByteArray()
      os.write(bytes.size.toByteArray(ByteOrder.BIG_ENDIAN))
      os.write(bytes)
    }
  }

  biliWSClient.connect()
}