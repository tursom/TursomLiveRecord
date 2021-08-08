package cn.tursom.record.saver

import cn.tursom.channel.enhance.ChannelWriter
import cn.tursom.core.buffer.ByteBuffer

@Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
interface LiveSaver : ChannelWriter<ByteBuffer> {
  override suspend fun write(buffer: ByteBuffer)

  override suspend fun write(vararg buffer: ByteBuffer) {
    super.write(value = buffer)
  }

  override suspend fun write(buffer: Collection<ByteBuffer>) {
    super.write(buffer)
  }

  override suspend fun writeAndFlush(buffer: ByteBuffer, timeout: Long): Long {
    return super.writeAndFlush(buffer, timeout)
  }
}