package cn.tursom.record

import cn.tursom.core.toInt
import cn.tursom.danmu.Danmu
import com.google.protobuf.TextFormat
import java.io.File
import java.nio.ByteOrder

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
    println(message)
    count++
    println(count)
    println(inputStream.available())
  }
}