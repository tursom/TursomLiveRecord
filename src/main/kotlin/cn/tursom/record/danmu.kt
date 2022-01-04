package cn.tursom.record

import cn.tursom.core.*
import cn.tursom.log.impl.Slf4jImpl
import cn.tursom.record.util.OnCloseCallbackOutputStream
import cn.tursom.ws.BiliWSClient
import cn.tursom.ws.CmdEnum
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
  os: () -> OutputStream,
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
  biliWSClient.addGiftListener {
    runBlocking {
      danmuChannel.send(Record.RecordMsg.newBuilder()
        .setGift(Record.GiftRecord.newBuilder()
          .setRoomId(biliWSClient.roomId)
          .setGift(it.toProto()))
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

  biliWSClient.addCmdListener(CmdEnum.PREPARING) {
    runBlocking {
      danmuChannel.send(Record.RecordMsg.newBuilder()
        .setLiveStatus(Record.LiveStatus.newBuilder()
          .setRoomId(biliWSClient.roomId)
          .setStatus(Record.LiveStatus.LiveStatusEnum.PREPARING))
        .build())
    }
  }

  GlobalScope.launch {
    var danmuInfo: Record.RecordMsg? = null
    while (true) {
      try {
        os().use { os ->
          while (true) {
            if (danmuInfo == null) {
              danmuInfo = danmuChannel.receive()
            }
            val bytes = danmuInfo!!.let { danmuInfo ->
              when (danmuInfo.contentCase) {
                Record.RecordMsg.ContentCase.DANMU -> println("${biliWSClient.userInfo.info.uname}: ${danmuInfo.danmu.danmu.userInfo.nickname}: ${danmuInfo.danmu.danmu.danmu}")
                Record.RecordMsg.ContentCase.LIVESTATUS -> println("${biliWSClient.userInfo.info.uname}: ${
                  when (danmuInfo.liveStatus.status) {
                    Record.LiveStatus.LiveStatusEnum.NONE -> "未知直播状态"
                    Record.LiveStatus.LiveStatusEnum.LIVE -> "开播"
                    Record.LiveStatus.LiveStatusEnum.PREPARING -> "下播"
                    Record.LiveStatus.LiveStatusEnum.UNRECOGNIZED -> "未知直播状态"
                    null -> "未知直播状态"
                  }
                }")
                Record.RecordMsg.ContentCase.GIFT -> println("${biliWSClient.userInfo.info.uname}: ${danmuInfo.gift.gift.uname}: ${danmuInfo.gift.gift.giftName} x ${danmuInfo.gift.gift.num}")
                Record.RecordMsg.ContentCase.CONTENT_NOT_SET -> Unit
                null -> Unit
              }

              danmuInfo.toByteArray()
            }
            os.write(bytes.size.toByteArray(ByteOrder.BIG_ENDIAN))
            os.write(bytes)
            os.flush()
            danmuInfo = null
          }
        }
      } catch (e: Throwable) {
        logger.error("an exception caused on save danmu", e)
      }
    }
  }
}

@OptIn(DelicateCoroutinesApi::class)
private fun startDanmuRecord(
  biliWSClient: BiliWSClient,
  out: () -> OutputStream,
) {
  var os: OutputStream
  os = OnCloseCallbackOutputStream(GZIPOutputStream(out()), object : () -> Unit {
    override fun invoke() {
      os = OnCloseCallbackOutputStream(GZIPOutputStream(out()), this)
    }
  })
  ShutdownHook.addHook {
    os.flush()
    os.close()
  }
  biliWSClient.addCmdListener(CmdEnum.PREPARING) {
    os.close()
  }
  GlobalScope.launch {
    recordDanmu(biliWSClient) {
      os
    }
  }
}

suspend fun recordDanmu(
  roomId: Int,
  out: () -> OutputStream,
): BiliWSClient {
  val biliWSClient = BiliWSClient(roomId)
  startDanmuRecord(biliWSClient, out)
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
