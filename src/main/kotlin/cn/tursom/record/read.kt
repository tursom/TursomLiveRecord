package cn.tursom.record

import cn.tursom.core.TextColor
import cn.tursom.core.ThreadLocalSimpleDateFormat
import cn.tursom.core.toInt
import com.google.protobuf.TextFormat
import java.io.File
import java.nio.ByteOrder

val dateFormat = ThreadLocalSimpleDateFormat.cn

fun printDanmu(danmuRecord: Record.DanmuRecord) {
  println(TextColor.run {
    "$green${dateFormat.format(danmuRecord.danmu.metadata.time)}$reset [$blue${
      danmuRecord.danmu.userInfo.nickname
    }$reset] - ${danmuRecord.danmu.danmu}"
  })
}

fun main() {
  setDefaultTextFormatPrinter(TextFormat.printer().escapingNonAscii(false))

  val inputStream = File("早见咲.rec").inputStream().buffered()
  val sizeBuf = ByteArray(4)
  var count = 0
  while (inputStream.available() > 0) {
    inputStream.read(sizeBuf)
    val size = sizeBuf.toInt(0, ByteOrder.BIG_ENDIAN)
    val buf = ByteArray(size)
    inputStream.read(buf)
    val message = Record.RecordMsg.parseFrom(buf)
    // println(message)
    count++
    print(count)
    print(" ")
    when (message.contentCase) {
      Record.RecordMsg.ContentCase.DANMU -> {
        printDanmu(message.danmu)
      }
      Record.RecordMsg.ContentCase.LIVESTATUS -> Unit
      Record.RecordMsg.ContentCase.CONTENT_NOT_SET -> Unit
      null -> Unit
    }
    // println(inputStream.available())
  }
}