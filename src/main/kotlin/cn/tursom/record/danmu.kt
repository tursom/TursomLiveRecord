package cn.tursom.record

import cn.tursom.core.ShutdownHook
import cn.tursom.core.final
import cn.tursom.core.toByteArray
import cn.tursom.log.impl.Slf4jImpl
import cn.tursom.ws.BiliWSClient
import com.google.protobuf.TextFormat
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.ByteOrder

private val logger = Slf4jImpl.getLogger()
private val roomIdList = intArrayOf(22340341, 1138, 23018529, 917818)

fun setDefaultTextFormatPrinter(printer: TextFormat.Printer) {
  val defaultPrinter = TextFormat.Printer::class.java.getDeclaredField("DEFAULT")
  defaultPrinter.isAccessible = true
  defaultPrinter.final = false
  defaultPrinter.set(null, printer)
}

@Suppress("BlockingMethodInNonBlockingContext")
@OptIn(ExperimentalUnsignedTypes::class, DelicateCoroutinesApi::class)
suspend fun main() {
  setDefaultTextFormatPrinter(TextFormat.printer().escapingNonAscii(false))

  val danmuChannel = Channel<Record.RecordMsg>(128)
  GlobalScope.launch {
    val os = File("danmu.rec").outputStream().buffered()
    ShutdownHook.addHook {
      os.flush()
      os.close()
    }

    while (true) {
      val danmuInfo = danmuChannel.receive()
      val bytes = danmuInfo.toByteArray()
      os.write(bytes.size.toByteArray(ByteOrder.BIG_ENDIAN))
      os.write(bytes)
      os.flush()
    }
  }

  val biliWSClient = BiliWSClient(22340341)
  biliWSClient.addDanmuListener {
    runBlocking {
      danmuChannel.send(Record.RecordMsg.newBuilder()
        .setDanmu(Record.DanmuRecord.newBuilder()
          .setRoomId(biliWSClient.roomId)
          .setDanmu(it.toProtobuf()))
        .build())
    }
  }

  biliWSClient.addLivingListener {
    runBlocking {
      danmuChannel.send(Record.RecordMsg.newBuilder()
        .setLiveStatus(Record.LiveStatus.newBuilder()
          .setRoomId(biliWSClient.roomId)
          .setStatus(Record.LiveStatus.LiveStatusEnum.LIVE))
        .build())
    }
  }


  biliWSClient.connect()
}