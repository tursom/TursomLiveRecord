package cn.tursom.record.saver

import cn.tursom.core.buffer.ByteBuffer

interface LiveSaver {
  suspend fun save(buffer: ByteBuffer)
  suspend fun finish()
}