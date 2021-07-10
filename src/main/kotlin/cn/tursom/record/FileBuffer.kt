package cn.tursom.record

import cn.tursom.core.buffer.ByteBuffer
import java.io.RandomAccessFile

class FileBuffer(
  private val file: String,
) {
  private val raf = RandomAccessFile(file, "rw")

  suspend fun write(buffer: ByteBuffer) {
  }

  suspend fun read(): ByteBuffer {
    TODO()
  }
}