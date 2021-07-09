package cn.tursom.record.provider

import cn.tursom.core.buffer.ByteBuffer

interface LiveProvider {
  suspend fun getData(): ByteBuffer
  suspend fun finish()
}