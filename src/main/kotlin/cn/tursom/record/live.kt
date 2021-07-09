package cn.tursom.record

import cn.tursom.core.buffer.ByteBuffer
import cn.tursom.core.seconds
import cn.tursom.log.impl.Slf4jImpl
import cn.tursom.record.provider.BilibiliLiveProvider
import cn.tursom.record.saver.FileLiveSaver
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlin.random.Random

private val logger = Slf4jImpl.getLogger()
private val retryDelaySecondsList = intArrayOf(5, 5, 10, 10, 30, 30, 60, 60, 60, 600)

suspend fun main() {
  val fileMaxSize = 4L * 1024 * 1024 * 1024

  var dataChannel = Channel<ByteBuffer>(128)
  var liveSaver = FileLiveSaver("23018529-${System.currentTimeMillis()}.flv", dataChannel, fileMaxSize)
  var liveProvider = BilibiliLiveProvider(23018529, dataChannel)
  liveProvider.onException = {
    liveProvider.finish()
    liveSaver.finish()
    val onException = liveProvider.onException
    dataChannel = Channel(128)

    val retryDelaySecondsIterator = retryDelaySecondsList.iterator()
    var waitSeconds = retryDelaySecondsIterator.nextInt()
    while (true) {
      delay((waitSeconds.seconds().toMillis() * Random.nextDouble(0.8, 1.2)).toLong())
      if (retryDelaySecondsIterator.hasNext()) {
        waitSeconds = retryDelaySecondsIterator.nextInt()
      }
      try {
        liveProvider = BilibiliLiveProvider(23018529, dataChannel, onException)
        liveProvider.startRecord()
        liveSaver = FileLiveSaver("23018529-${System.currentTimeMillis()}.flv", dataChannel, fileMaxSize, onException)
        break
      } catch (e: Exception) {
        logger.warn("an exception caused on reconnect to live server", e)
      }
    }
  }
  liveSaver.onException = liveProvider.onException
  liveProvider.startRecord()

  while (true) {
    delay(1000)
  }
}