package cn.tursom.record

import cn.tursom.core.*
import cn.tursom.log.impl.Slf4jImpl
import cn.tursom.record.util.LazyOutputStream
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

private val logger = Slf4jImpl.getLogger()

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
        .setTime(System.currentTimeMillis())
        .setDanmu(Record.DanmuRecord.newBuilder()
          .setRoomId(biliWSClient.roomId)
          .setDanmu(it.toProtobuf()))
        .build())
    }
  }
  biliWSClient.addGiftListener {
    runBlocking {
      danmuChannel.send(Record.RecordMsg.newBuilder()
        .setTime(System.currentTimeMillis())
        .setGift(Record.GiftRecord.newBuilder()
          .setRoomId(biliWSClient.roomId)
          .setGift(it.toProto()))
        .build())
    }
  }

  biliWSClient.addLivingListener {
    runBlocking {
      danmuChannel.send(Record.RecordMsg.newBuilder()
        .setTime(System.currentTimeMillis())
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
    while (true) try {
      os().use { os ->
        while (true) {
          if (danmuInfo == null) {
            danmuInfo = danmuChannel.receive()
          }
          val bytes = danmuInfo!!.let { danmuInfo ->
            when (danmuInfo.contentCase) {
              Record.RecordMsg.ContentCase.DANMU ->
                println("${biliWSClient.userInfo.info.uname}: ${danmuInfo.danmu.danmu.userInfo.nickname}: ${danmuInfo.danmu.danmu.danmu}")
              Record.RecordMsg.ContentCase.LIVESTATUS -> println("${biliWSClient.userInfo.info.uname}: ${
                when (danmuInfo.liveStatus.status) {
                  Record.LiveStatus.LiveStatusEnum.NONE -> "??????????????????"
                  Record.LiveStatus.LiveStatusEnum.LIVE -> "??????"
                  Record.LiveStatus.LiveStatusEnum.PREPARING -> "??????"
                  Record.LiveStatus.LiveStatusEnum.UNRECOGNIZED -> "??????????????????"
                  null -> "??????????????????"
                }
              }")
              Record.RecordMsg.ContentCase.GIFT ->
                println("${biliWSClient.userInfo.info.uname}: ${danmuInfo.gift.gift.uname}: ${danmuInfo.gift.gift.giftName} x ${danmuInfo.gift.gift.num}")
              else -> Unit
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

@OptIn(DelicateCoroutinesApi::class)
private fun startDanmuRecord(
  biliWSClient: BiliWSClient,
  out: () -> OutputStream,
) {
  var os: OutputStream
  os = OnCloseCallbackOutputStream(out(), object : () -> Unit {
    override fun invoke() {
      os = OnCloseCallbackOutputStream(out(), this)
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
  startDanmuRecord(biliWSClient) {
    LazyOutputStream(out)
  }
  println("connect $roomId ${biliWSClient.userInfo.info.uname}")
  biliWSClient.connect()
  return biliWSClient
}


val danmuRecordRooms = listOf(
  1138 to "????????????",
  23018529 to "??????yami??????",
  917818 to "tursom??????",
  10413051 to "???????????????_usagi ??????",
  14197798 to "??????Ankii ??????",
  4767523 to "????????????",
  1346192 to "??????????????????",
  1016818 to "?????????????????????",
  292397 to "????????????",
  7906153 to "??????????????? ??????",
  22603245 to "????????????",
  5555734 to "ArkNights",
  367966 to "??????",
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
