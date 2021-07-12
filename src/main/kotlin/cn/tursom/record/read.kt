package cn.tursom.record

import cn.tursom.core.TextColor
import cn.tursom.core.ThreadLocalSimpleDateFormat
import cn.tursom.core.toInt
import cn.tursom.danmu.Danmu
import com.google.protobuf.TextFormat
import java.io.File
import java.nio.ByteOrder

private val dateFormat = ThreadLocalSimpleDateFormat.cn

fun printDanmu(danmuRecord: Danmu.DanmuInfo) {
  println(TextColor.run {
    "$green${dateFormat.format(danmuRecord.metadata.time)}$reset [$blue${
      danmuRecord.userInfo.nickname
    }$reset] - ${danmuRecord.danmu}"
  })
}

fun main() {
  setDefaultTextFormatPrinter(TextFormat.printer().escapingNonAscii(false))

  val inputStream = File("danmu.rec").inputStream().buffered()
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
        printDanmu(message.danmu.danmu)
      }
      Record.RecordMsg.ContentCase.LIVESTATUS -> Unit
      Record.RecordMsg.ContentCase.CONTENT_NOT_SET -> Unit
      null -> Unit
    }
    // println(inputStream.available())
  }
}