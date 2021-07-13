package cn.tursom.record.checker

import cn.tursom.core.buffer.ByteBuffer
import cn.tursom.core.buffer.getIntWithSize
import cn.tursom.core.buffer.impl.HeapByteBuffer
import cn.tursom.core.pool.HeapMemoryPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import java.nio.ByteOrder
import kotlin.coroutines.EmptyCoroutineContext

class JavaFlvChecker : FlvChecker {
  companion object {
    private val flvHeader = byteArrayOf(0x46, 0x4C, 0x56)

    fun isValidFlvHeader(buf: ByteBuffer): Boolean {
      if (buf.readable < 9) return false
      if (buf.get() != 0x46.toByte() || buf.get() != 0x4C.toByte() || buf.get() != 0x56.toByte()) {
        return false
      }
      val flvVer = buf.get()
      val flvType = buf.get().toInt()
      if (flvVer.toInt() != 0x01) return false
      return if (flvType != 0x01 && flvType != 0x04 && flvType != 0x05) {
        false
      } else {
        buf.get() == 0x00.toByte() && buf.get() == 0x00.toByte() &&
          buf.get() == 0x00.toByte() && buf.get() == 0x09.toByte()
      }
    }
  }

  private val dataChannel = Channel<ByteBuffer>()
  private val resultChannel = Channel<ByteBuffer>(16)
  private val memoryPool = HeapMemoryPool(256 * 1024)
  private var writeBuf = memoryPool.get()
  private val coroutineScope = CoroutineScope(EmptyCoroutineContext)
  private val dataBuf = HeapByteBuffer(512 * 1024)

  init {
    coroutineScope.launch {
      var recvData = dataChannel.receive()
      var lastTimestamp = 0L
      recvData.writeTo(dataBuf)
      while (dataBuf.readable < 9) {
        recvData = dataChannel.receive()
        recvData.writeTo(dataBuf)
      }
      if (!isValidFlvHeader(dataBuf)) {
        System.err.println("invalid flv header")
        return@launch
      }

      while (true) {
        while (dataBuf.readable < 9) {
          dataBuf.reset()
          if (recvData.readable != 0) {
            recvData.writeTo(dataBuf)
          } else {
            recvData = dataChannel.receive()
            recvData.writeTo(dataBuf)
          }
        }
        val tagData = TagData(dataBuf)
        if (tagData.timestamp < lastTimestamp) {
          System.err.println("an wrong timestamp")
        }
        lastTimestamp = tagData.timestamp

        while (dataBuf.readable < tagData.dataSize) {
          dataBuf.reset()
          if (recvData.readable != 0) {
            recvData.writeTo(dataBuf)
          } else {
            recvData = dataChannel.receive()
            //println(recvData)
            recvData.writeTo(dataBuf)
          }
          //println(dataBuf)
        }
        //tagData.body = dataBuf.slice(dataBuf.readPosition, tagData.dataSize, 0, tagData.dataSize)
        println(tagData)
        dataBuf.skip(tagData.dataSize)
        // dataBuf.reset()
      }
    }
  }

  override suspend fun put(buffer: ByteBuffer) {
    dataChannel.send(buffer)
  }

  override suspend fun read(): ByteBuffer = resultChannel.receive()

  enum class TagType(val code: Int) {
    AUDIO(0x08), VIDEO(0x09), SCRIPT(0x12);

    companion object {
      fun valueOf(code: Int): TagType = when (code) {
        0x08 -> AUDIO
        0x09 -> VIDEO
        0x12 -> SCRIPT
        else -> throw IllegalArgumentException("No enum constant cn.tursom.record.checker.JavaFlvChecker.TagType.code = $code")
      }
    }
  }

  data class TagData(
    val prevSize: Int,
    val type: TagType,
    val dataSize: Int,
    val timestamp: Long,
    val streamId: Int,
    var body: ByteBuffer? = null,
  ) {
    constructor(buf: ByteBuffer) : this(
      buf.getInt(ByteOrder.BIG_ENDIAN),
      TagType.valueOf(buf.get().toInt()),
      buf.getIntWithSize(3, ByteOrder.BIG_ENDIAN),
      buf.getInt(ByteOrder.BIG_ENDIAN).toLong() and 0x7fff_ffff_ffff_ffff,
      buf.getIntWithSize(3, ByteOrder.BIG_ENDIAN),
    )
  }
}

suspend fun main() {
  val flvChecker = JavaFlvChecker()
  val inputStream =
    File("C:\\Users\\tursom\\IdeaProjects\\TursomLiveRecord\\record\\盐咪yami录播-2021-07-13 21-18-31.flv").inputStream()
  try {
    while (true) {
      val bytes = ByteArray(1024)
      val read = inputStream.read(bytes)
      //println(read)
      val buffer = HeapByteBuffer(bytes, 0, read)
      //delay(100)
      flvChecker.put(buffer)
    }
  } catch (e: Exception) {
  }

}
