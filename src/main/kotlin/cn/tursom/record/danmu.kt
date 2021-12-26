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
import java.util.zip.GZIPOutputStream

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
  biliWSClient.addCmdListener("PREPARING") {
    danmuChannel.close()
  }

  GlobalScope.launch {
    os.use {
      while (true) {
        val danmuInfo = danmuChannel.receive()

        println("${danmuInfo.danmu.danmu.userInfo.nickname}: ${danmuInfo.danmu.danmu.danmu}")
        val bytes = danmuInfo.toByteArray()
        os.write(bytes.size.toByteArray(ByteOrder.BIG_ENDIAN))
        os.write(bytes)
        os.flush()
      }
    }
  }
}

@OptIn(DelicateCoroutinesApi::class)
private fun startDanmuRecord(
  biliWSClient: BiliWSClient,
  out: () -> OutputStream,
) {
  val os = GZIPOutputStream(out())
  ShutdownHook.addHook {
    os.flush()
    os.close()
  }

  GlobalScope.launch {
    recordDanmu(biliWSClient, os)
  }
}

suspend fun recordDanmu(
  roomId: Int,
  out: () -> OutputStream,
): BiliWSClient {
  val biliWSClient = BiliWSClient(roomId)
  biliWSClient.addLivingListener {
    startDanmuRecord(biliWSClient, out)
  }
  if (biliWSClient.living) {
    startDanmuRecord(biliWSClient, out)
  }

  println("connect $roomId ${biliWSClient.userInfo.info.uname}")
  biliWSClient.connect()
  return biliWSClient
}


val danmuRecordRooms = listOf(
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
  367966 to "小触",
  917818 to "tursom",
)

private val dateFormat = ThreadLocalSimpleDateFormat("YYYY-MM-dd HH")

@Suppress("BlockingMethodInNonBlockingContext")
@OptIn(ExperimentalUnsignedTypes::class, DelicateCoroutinesApi::class)
suspend fun main() {
  setDefaultTextFormatPrinter(TextFormat.printer().escapingNonAscii(false))

  danmuRecordRooms.forEach { (roomId, file) ->
    recordDanmu(roomId) { File("$file-${dateFormat.now()}.rec").outputStream() }
  }
  while (true) {
    Thread.sleep(1.seconds().toMillis())
  }
}
