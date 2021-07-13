package cn.tursom.record.checker

import cn.tursom.core.buffer.ByteBuffer

interface FlvChecker {
  suspend fun put(buffer: ByteBuffer)
  suspend fun read(): ByteBuffer
}
