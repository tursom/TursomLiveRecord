package cn.tursom.record

import cn.tursom.core.*
import cn.tursom.log.impl.Slf4jImpl
import cn.tursom.ws.BiliWSClient
import com.google.protobuf.TextFormat
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.OutputStream
import java.nio.ByteOrder

private val logger = Slf4jImpl.getLogger()
private val roomIdList = intArrayOf(22340341, 1138, 23018529, 917818)

fun setDefaultTextFormatPrinter(printer: TextFormat.Printer) {
  val defaultPrinter = TextFormat.Printer::class.java.getDeclaredField("DEFAULT")
  defaultPrinter.isAccessible = true
  defaultPrinter.final = false
  defaultPrinter.set(null, printer)
}

@OptIn(DelicateCoroutinesApi::class)
@Suppress("BlockingMethodInNonBlockingContext")
suspend fun recordDanmu(
  biliWSClient: BiliWSClient,
  os: OutputStream,
) {
  val danmuChannel = Channel<Record.RecordMsg>(64)
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

  GlobalScope.launch {
    os.use {
      while (true) {
        val danmuInfo = danmuChannel.receive()
        val bytes = danmuInfo.toByteArray()
        os.write(bytes.size.toByteArray(ByteOrder.BIG_ENDIAN))
        os.write(bytes)
        os.flush()
      }
    }
  }
}

suspend fun recordDanmu(
  roomId: Int,
  file: String,
): BiliWSClient {
  val biliWSClient = BiliWSClient(roomId)
  val os = File(file).outputStream().buffered()
  ShutdownHook.addHook {
    os.flush()
    os.close()
  }

  recordDanmu(biliWSClient, os)

  println("connect")
  biliWSClient.connect()
  return biliWSClient
}


private val recordRooms = listOf(
  1138 to "乌拉录播",
  23018529 to "盐咪yami录播",
  917818 to "tursom录播",
  10413051 to "宇佐紀ノノ_usagi 录播",
  14197798 to "安晴Ankii 录播",
  4767523 to "沙月录播",
  1346192 to "潮留芥末录播",
  1016818 to "猫屋敷梨梨录播",
  292397 to "巫贼录播",
  7906153 to "喵枫にゃぁ 录播",
  22603245 to "塔菲录播",
  5555734 to "ArkNights",
)

private val dateFormat = ThreadLocalSimpleDateFormat("YYYY-MM-dd HH")

@Suppress("BlockingMethodInNonBlockingContext")
@OptIn(ExperimentalUnsignedTypes::class, DelicateCoroutinesApi::class)
suspend fun main() {
  setDefaultTextFormatPrinter(TextFormat.printer().escapingNonAscii(false))

  recordRooms.forEach { (roomId, file) ->
    recordDanmu(roomId, "$file.rec")
  }
  while (true) {
    Thread.sleep(1.seconds().toMillis())
  }
}
