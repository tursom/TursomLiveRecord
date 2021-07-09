package cn.tursom.record.saver

import cn.tursom.core.buffer.ByteBuffer
import cn.tursom.log.impl.Slf4jImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext

class FileLiveSaver(
  private val path: String,
  private val bufferChannel: Channel<ByteBuffer> = Channel(128),
  private val maxSize: Long = Long.MAX_VALUE,
  var onException: suspend (e: Exception) -> Unit = {},
) : LiveSaver {
  companion object : Slf4jImpl()

  private val os = File(path).outputStream()
  private val coroutineScope = CoroutineScope(EmptyCoroutineContext)

  private var savedSize = 0

  init {
    coroutineScope.launch(Dispatchers.IO) {
      while (true) {
        val buffer = bufferChannel.receive()
        while (buffer.readable != 0) {
          savedSize += buffer.writeTo(os)
        }
        buffer.close()
        logger.debug("file {} save buffer", path)

        if (savedSize >= maxSize) {
          finish()
          onException(Exception("file write finished"))
        }
      }
    }
  }

  override suspend fun save(buffer: ByteBuffer) {
    bufferChannel.send(buffer)
  }

  override suspend fun finish() {
    try {
      (bufferChannel as ReceiveChannel<*>).cancel()
    } catch (e: Exception) {
    }
    coroutineScope.launch(Dispatchers.IO) {
      os.close()
      try {
        coroutineScope.cancel()
      } catch (e: Exception) {
      }
    }
  }
}
