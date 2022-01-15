package cn.tursom.record.util

import cn.tursom.core.toInt
import cn.tursom.record.Record
import java.io.InputStream
import java.nio.ByteOrder

private fun InputStream.readInt(
  byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN,
  intBuffer: ByteArray = ByteArray(4),
): Int {
  intBuffer[0] = read().toByte()
  intBuffer[1] = read().toByte()
  intBuffer[2] = read().toByte()
  intBuffer[3] = read().toByte()
  return intBuffer.toInt(byteOrder = byteOrder)
}


private fun InputStream.readOrFail(buffer: ByteArray) {
  var read = 0
  while (read != buffer.size) {
    read += read(buffer, read, buffer.size - read)
  }
}

fun InputStream.loopRecord(callback: (Record.RecordMsg) -> Unit) {
  val intBuffer = ByteArray(4)
  use { inputStream ->
    try {
      while (true) {
        val size = inputStream.readInt(intBuffer = intBuffer)
        val msg = run {
          val msgBuffer = ByteArray(size)
          inputStream.readOrFail(msgBuffer)
          Record.RecordMsg.parseFrom(msgBuffer)
        }
        callback(msg)
      }
    } catch (_: Exception) {
    }
  }
}

fun InputStream.recTime(): Pair<Long, Long> {
  var start = -1L
  var end = -1L
  loopRecord {
    if (start < 0) {
      start = it.time
    } else {
      end = it.time
    }
  }
  return start to if (end < 0) start else end
}
