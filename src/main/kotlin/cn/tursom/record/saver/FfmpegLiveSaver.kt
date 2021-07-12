package cn.tursom.record.saver

import cn.tursom.core.buffer.ByteBuffer
import cn.tursom.core.pool.HeapMemoryPool
import cn.tursom.log.debugEnabled
import cn.tursom.log.impl.Slf4jImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlin.coroutines.EmptyCoroutineContext

class FfmpegLiveSaver(
  private val prevSaver: LiveSaver,
  fileType: String = "flv",
  private val dataChannel: Channel<ByteBuffer> = Channel(32),
  val ffmpegDecoderArgs: Array<String> = arrayOf(),
  vararg ffmpegArgs: String = arrayOf(
    "-speed", "0",
    "-c:a", "copy",
    "-c:v", "h264_nvenc",
  ),
  //var onException: suspend (e: Exception) -> Unit = {},
) : LiveSaver {
  @Suppress("unused", "MemberVisibilityCanBePrivate", "SpellCheckingInspection")
  enum class VSyncEnum(val code: String) {
    PASSTHROUGH("0"), CFR("1"), VFR("2"), DROP("drop"), AUTO("-1");

    val param = arrayOf("-vf", "scale=$code")
  }

  @Suppress("unused", "MemberVisibilityCanBePrivate", "SpellCheckingInspection")
  enum class PresetEnum(val code: String) {
    VERY_SLOW("veryslow"), SLOWER("slower"), SLOW("slow"), MEDIUM("medium"),
    FAST("fast"), FASTER("faster"), VERY_FAST("veryfast"), SUPER_FAST("superfast"),
    ULTRA_FAST("ultrafast"),
    ;

    val param = arrayOf("-preset", code)
  }

  @Suppress("unused", "MemberVisibilityCanBePrivate")
  companion object : Slf4jImpl() {
    const val yes = "-y"

    val s360p = scale("640:360")
    val s720p = scale("1280:720")
    val s1080p = scale("1920:1080")

    val nvidiaDecoder = videoDecoder("h264_cuvid")

    val nvidiaEncoder = videoEncoder("h264_nvenc")
    val qsvEncoder = videoEncoder("h264_qsv")
    val x264Encoder = videoEncoder("libx264")
    val x265Encoder = videoEncoder("libx265")

    val copyAudioEncoder = audioEncoder("copy")

    val f30 = frame(30)
    val f60 = frame(60)

    fun speed(speed: Int) = arrayOf("-speed", speed.toString())
    fun videoDecoder(encoder: String) = arrayOf("-c:v", encoder)
    fun videoEncoder(encoder: String) = arrayOf("-c:v", encoder)
    fun audioEncoder(encoder: String) = arrayOf("-c:a", encoder)
    fun byteRage(rate: String) = arrayOf("-b:v", rate)
    fun vsync(type: VSyncEnum) = arrayOf("-vsync", type.code)
    fun scale(scale: String) = arrayOf("-vf", "scale=$scale")
    fun frame(frame: Int) = arrayOf("-r", frame.toString())
    fun frame(frame: Float) = arrayOf("-r", frame.toString())
    fun frame(frame: String) = arrayOf("-r", frame)
  }

  private val process = Runtime.getRuntime()
    .exec(arrayOf(
      "ffmpeg",
      *ffmpegDecoderArgs,
      "-i", "pipe:0",
      *ffmpegArgs,
      "-f", fileType,
      "pipe:1",
    ))
  private val inputStream = process.inputStream
  private val outputStream = process.outputStream
  private val coroutineScope = CoroutineScope(EmptyCoroutineContext)
  private val bufferPool = HeapMemoryPool(256 * 1024, 8)

  init {
    if (logger.debugEnabled) coroutineScope.launch(Dispatchers.IO) {
      process.errorStream.use { es ->
        while (true) {
          val line = es.bufferedReader().readLine() ?: break
          logger.debug("ffmpeg: {}", line)
        }
      }
    }
    coroutineScope.launch(Dispatchers.IO) {
      try {
        inputStream.use {
          while (true) {
            val buffer = bufferPool.get()
            while (buffer.isWriteable) {
              buffer.put(inputStream)
            }
            prevSaver.save(buffer)
          }
        }
      } catch (e: ClosedReceiveChannelException) {
      } finally {
        finish()
      }
    }
    coroutineScope.launch(Dispatchers.IO) {
      try {
        outputStream.use {
          val byteArrayBuf = ByteArray(1024)
          while (true) {
            val buffer = dataChannel.receive()
            while (buffer.isReadable) {
              buffer.writeTo(outputStream, byteArrayBuf)
            }
            buffer.close()
          }
        }
      } catch (e: ClosedReceiveChannelException) {
      } finally {
        finish()
      }
    }
  }

  override suspend fun save(buffer: ByteBuffer) {
    dataChannel.send(buffer)
  }

  @Suppress("BlockingMethodInNonBlockingContext")
  override suspend fun finish() {
    try {
      outputStream.close()
    } catch (e: Exception) {
    }
    try {
      inputStream.close()
    } catch (e: Exception) {
    }
    try {
      process.destroy()
    } catch (e: Exception) {
    }
    prevSaver.finish()
    try {
      coroutineScope.cancel()
    } catch (e: Exception) {
    }
  }
}