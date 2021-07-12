package cn.tursom.record.provider

import cn.tursom.core.buffer.ByteBuffer
import kotlinx.coroutines.channels.Channel

class NettyBilibiliLiveProvider(
  private val roomId: Int,
  log: Boolean = false,
  compressed: Boolean = true,
  headers: Map<String, String>? = null,
  dataChannel: Channel<ByteBuffer> = Channel(32),
  private val highQn: Boolean = false,
  onException: suspend (e: Exception) -> Unit = {},
) : NettyLiveProvider(log, compressed, headers, dataChannel, onException) {
  suspend fun startRecord() {
    startRecord(BilibiliLiveProvider.getLiveUrl(roomId, highQn))
  }
}