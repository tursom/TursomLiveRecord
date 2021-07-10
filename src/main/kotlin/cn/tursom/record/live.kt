package cn.tursom.record

import cn.tursom.core.ThreadLocalSimpleDateFormat
import cn.tursom.core.buffer.ByteBuffer
import cn.tursom.core.seconds
import cn.tursom.log.impl.Slf4jImpl
import cn.tursom.record.provider.BilibiliLiveProvider
import cn.tursom.record.saver.FfmpegLiveSaver
import cn.tursom.record.saver.FileLiveSaver
import cn.tursom.record.saver.LiveSaver
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import java.io.IOException
import kotlin.random.Random

private val logger = Slf4jImpl.getLogger()
private val retryDelaySecondsList = intArrayOf(5, 5, 10, 10, 30, 30, 60, 60, 60)
private val dateFormat = ThreadLocalSimpleDateFormat("YYYY-MM-dd HH-mm-ss")

private const val fileMaxSize = 4L * 1024 * 1024 * 1024

private val recordRooms = listOf(
  //1138 to "乌拉录播",
  23018529 to "盐咪yami录播",
  //917818 to "tursom录播",
  //10413051 to "宇佐紀ノノ_usagi 录播",
  //14197798 to "安晴Ankii 录播",
)

suspend fun startRecord(roomId: Int, title: String) {
  var dataChannel = Channel<ByteBuffer>(128)
  var liveSaver: LiveSaver? = null
  var liveProvider = BilibiliLiveProvider(roomId, dataChannel)
  val fileType = "flv"
  //val ffmpegArgs = FfmpegLiveSaver.speed(0) + FfmpegLiveSaver.nvidiaEncoder +
  //  FfmpegLiveSaver.byteRage("2000k") + FfmpegLiveSaver.s720p
  val ffmpegArgs = FfmpegLiveSaver.x264Encoder + FfmpegLiveSaver.byteRage("2000k") +
    FfmpegLiveSaver.PresetEnum.MEDIUM.param + FfmpegLiveSaver.s720p
  //val ffmpegArgs = FfmpegLiveSaver.speed(6) + FfmpegLiveSaver.x265Encoder +
  //  FfmpegLiveSaver.copyAudioEncoder + arrayOf("-movflags", "frag_keyframe+empty_moov")

  liveProvider.onException = {
    logger.info("disconnected, reconnecting")
    liveProvider.finish()
    liveSaver?.finish()
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
        liveProvider = BilibiliLiveProvider(roomId, dataChannel, onException = onException)
        liveProvider.startRecord()
        liveSaver = FfmpegLiveSaver(
          FileLiveSaver("$title-${dateFormat.now()}.flv", maxSize = fileMaxSize, onException = onException),
          fileType, dataChannel, ffmpegArgs = ffmpegArgs
        )
        break
      } catch (e: IOException) {
        logger.info("room live not started")
      } catch (e: Exception) {
        logger.warn("an exception caused on reconnect to live server", e)
      }
    }
  }
  try {
    liveProvider.startRecord()
    liveSaver = FfmpegLiveSaver(
      FileLiveSaver("$title-${dateFormat.now()}.flv", maxSize = fileMaxSize),
      fileType, dataChannel, ffmpegArgs = ffmpegArgs
    )
  } catch (e: Exception) {
    liveProvider.onException(e)
  }
}

suspend fun main() {
  recordRooms.forEach { (roomId, title) ->
    startRecord(roomId, title)
  }

  while (true) {
    delay(1000)
  }
}