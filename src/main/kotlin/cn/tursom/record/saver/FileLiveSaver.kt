package cn.tursom.record.saver

import cn.tursom.core.AsyncFile
import cn.tursom.core.util.ShutdownHook
import cn.tursom.core.buffer.ByteBuffer
import cn.tursom.log.impl.Slf4jImpl
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import java.io.IOException
import kotlin.coroutines.EmptyCoroutineContext

class FileLiveSaver(
  private val path: String,
  private val dataChannel: Channel<ByteBuffer> = Channel(32),
  private val maxSize: Long = Long.MAX_VALUE,
  var onException: suspend (e: Exception) -> Unit = {},
) : LiveSaver {
  companion object : Slf4jImpl()

  private val os = AsyncFile(path)
  private val coroutineScope = CoroutineScope(EmptyCoroutineContext)
  private var savedSize: Long = 0

  @Suppress("unused")
  private val hook = ShutdownHook.addSoftShutdownHook {
    close()
  }

  init {
    logger.info("create record file {} result {}", path, os.create())
    coroutineScope.launch {
      try {
        while (true) {
          val buffer = dataChannel.receive()
          while (buffer.isReadable) {
            val writeSize = os.writeAndWait(buffer)
            if (writeSize <= 0) throw IOException("file closed")
            savedSize += writeSize
          }
          buffer.close()
          logger.debug("file {} save buffer. saved {} M", path, savedSize / (1024 * 1024))

          if (savedSize >= maxSize) {
            close()
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) {
              onException(Exception("file write finished"))
            }
          }
        }
      } catch (e: ClosedReceiveChannelException) {
      } catch (e: Exception) {
        logger.warn("an exception caused on save file", e)
      } finally {
        close()
      }
    }
  }

  override suspend fun write(buffer: ByteBuffer) {
    dataChannel.send(buffer)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun close() {
    try {
      if (!(dataChannel.isClosedForReceive || dataChannel.isClosedForSend)) {
        (dataChannel as ReceiveChannel<*>).cancel()
      }
    } catch (e: Exception) {
    }
    try {
      @Suppress("BlockingMethodInNonBlockingContext")
      os.close()
    } catch (e: Exception) {
    }
    try {
      coroutineScope.cancel()
    } catch (e: Exception) {
    }
  }

  override suspend fun flush(timeout: Long): Long {
    return 0
  }
}
