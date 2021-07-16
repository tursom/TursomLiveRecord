package cn.tursom.record.provider

import cn.tursom.core.buffer.ByteBuffer
import kotlinx.coroutines.channels.Channel

interface LiveProvider {
  val dataChannel: Channel<ByteBuffer>
  suspend fun getData(): ByteBuffer = dataChannel.receive()
  suspend fun finish()
}