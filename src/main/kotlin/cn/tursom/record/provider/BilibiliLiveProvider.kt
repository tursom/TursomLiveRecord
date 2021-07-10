package cn.tursom.record.provider

import cn.tursom.core.buffer.ByteBuffer
import cn.tursom.core.buffer.impl.PooledByteBuffer
import cn.tursom.core.fromJson
import cn.tursom.core.pool.HeapMemoryPool
import cn.tursom.log.impl.Slf4jImpl
import cn.tursom.utils.AsyncHttpRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.EmptyCoroutineContext

class BilibiliLiveProvider(
  private val roomId: Int,
  private val dataChannel: Channel<ByteBuffer> = Channel(32),
  bufferBlockSize: Int = 256 * 1024,
  private val highQn: Boolean = false,
  var onException: suspend (e: Exception) -> Unit = {},
) : LiveProvider {
  companion object : Slf4jImpl()

  private val coroutineScope = CoroutineScope(EmptyCoroutineContext)
  private val bufferPool = HeapMemoryPool(bufferBlockSize)
  private var closed = false
  private var inputStream: InputStream? = null

  override suspend fun getData(): ByteBuffer {
    return dataChannel.receive()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  override suspend fun finish() {
    closed = true
    try {
      @Suppress("BlockingMethodInNonBlockingContext")
      inputStream?.close()
    } catch (e: Exception) {
    }
    try {
      (dataChannel as SendChannel<*>).close()
    } catch (e: Exception) {
    }
    try {
      coroutineScope.cancel()
    } catch (e: Exception) {
    }
  }

  suspend fun startRecord() {
    var liveJson = getLiveJson(roomId, "-1")
    if (highQn && liveJson.data.current_qn != liveJson.data.quality_description[0].qn) {
      liveJson = getLiveJson(roomId, liveJson.data.quality_description[0].qn.toString())
    }
    val url = liveJson.data.durl[0].url
    val response = AsyncHttpRequest.get(url)
    val inputStream = response.body()!!.byteStream()
    this.inputStream = inputStream
    var buffer = bufferPool.get()
    while (buffer.writeable != 0) {
      buffer.put(inputStream)
    }

    coroutineScope.launch(Dispatchers.IO) {
      try {
        while (!closed) {
          if (buffer !is PooledByteBuffer) {
            logger.warn("out of memory pool")
          }
          while (buffer.writeable != 0) {
            buffer.put(inputStream)
          }
          logger.debug("room {} provide buffer", roomId)
          dataChannel.send(buffer)
          buffer = bufferPool.get()
        }
      } catch (e: CancellationException) {
        logger.info("BilibiliLiveProvider canceled", e)
        inputStream.close()
      } catch (e: Exception) {
        logger.warn("an exception caused on provider data", e)
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
          onException(e)
        }
        finish()
      }
    }
  }

  private suspend fun getLiveJson(roomId: Int, qn: String): BiliLiveUrl {
    val url = "https://api.live.bilibili.com/room/v1/Room/playUrl?cid=$roomId&quality=$qn&platform=web"
    val biliLiveUrl = AsyncHttpRequest.getStr(url).fromJson<BiliLiveUrl>()
    return biliLiveUrl
  }

  data class BiliLiveUrl(
    val code: Int,
    val `data`: Data,
    val message: String,
    val ttl: Int,
  ) {
    data class Data(
      val accept_quality: List<String>,
      val current_qn: Int,
      val current_quality: Int,
      val durl: List<Durl>,
      val quality_description: List<QualityDescription>,
    )

    data class QualityDescription(
      val desc: String,
      val qn: Int,
    )

    data class Durl(
      val length: Int,
      val order: Int,
      val p2p_type: Int,
      val stream_type: Int,
      val url: String,
    )
  }
}

