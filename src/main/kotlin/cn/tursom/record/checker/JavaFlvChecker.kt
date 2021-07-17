package cn.tursom.record.checker

import cn.tursom.core.buffer.ByteBuffer
import cn.tursom.core.buffer.getIntWithSize
import cn.tursom.core.buffer.impl.HeapByteBuffer
import cn.tursom.core.buffer.putIntWithSize
import cn.tursom.core.pool.HeapMemoryPool
import cn.tursom.core.seconds
import cn.tursom.log.impl.Slf4jImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.nio.ByteOrder
import kotlin.coroutines.EmptyCoroutineContext

class JavaFlvChecker : FlvChecker {
  companion object : Slf4jImpl() {
    const val FLV_HEADER_SIZE = 9

    fun isValidFlvHeader(buf: ByteBuffer): Boolean {
      if (buf.readable < FLV_HEADER_SIZE) return false
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
  private val memoryPool = HeapMemoryPool(32 * 1024, 16)
  private var writeBuf = memoryPool.get()
  private val coroutineScope = CoroutineScope(EmptyCoroutineContext)
  private val dataBuf = HeapByteBuffer(32 * 1024)
  private var videoTimestamp = 0
  private var audioTimestamp = 0
  private var videoTimestampFix = 0
  private var audioTimestampFix = 0
  private var filePosition = 0L
  private var recvData: ByteBuffer = HeapByteBuffer(1)
  private var resultFrameCache: ByteBuffer? = null

  init {
    coroutineScope.launch {
      try {
        checkHead()
        while (true) {
          val tag = getTag() ?: continue
          writeTag(tag)
        }
      } catch (e: Exception) {
        logger.error("an exception caused on check flv file", e)
      }
    }
  }

  private suspend fun checkHead() {
    recvData = dataChannel.receive()
    recvData.writeTo(dataBuf)
    while (dataBuf.readable < FLV_HEADER_SIZE) {
      recvData.close()
      recvData = dataChannel.receive()
      recvData.writeTo(dataBuf)
    }

    if (!isValidFlvHeader(dataBuf.slice(dataBuf.readPosition, FLV_HEADER_SIZE, writePosition = FLV_HEADER_SIZE))) {
      throw InvalidFlvHeaderException()
    }

    dataBuf.slice(dataBuf.readPosition, FLV_HEADER_SIZE, writePosition = FLV_HEADER_SIZE).writeTo(writeBuf)
    resultChannel.send(writeBuf.slice(0, FLV_HEADER_SIZE, writePosition = FLV_HEADER_SIZE))
    dataBuf.readPosition += FLV_HEADER_SIZE
    writeBuf.readPosition += FLV_HEADER_SIZE
    filePosition += FLV_HEADER_SIZE
  }

  private suspend fun getTag(): FlvTag? {
    val tagData = getTagHeader()
    if (tagData == null) {
      resultFrameCache = null
      return null
    }
    checkTagTimestamp(tagData)

    logger.debug("get tag: {}", tagData)
    getTagBody(tagData)
    return tagData
  }

  private suspend fun getTagHeader(): FlvTag? {
    while (dataBuf.readable < FlvTag.TAG_HEADER_SIZE) {
      dataBuf.reset()
      if (recvData.readable != 0) {
        recvData.writeTo(dataBuf)
      } else {
        recvData.close()
        recvData = dataChannel.receive()
        recvData.writeTo(dataBuf)
      }
    }
    val tagData = FlvTag(dataBuf)
    filePosition += FlvTag.TAG_HEADER_SIZE
    return tagData
  }

  @Suppress("DuplicatedCode")
  private fun checkTagTimestamp(tag: FlvTag?) {
    tag ?: return
    when (tag.type) {
      TagType.AUDIO -> {
        val fixedTimestamp = tag.timestamp + audioTimestampFix
        if (fixedTimestamp < audioTimestamp || fixedTimestamp - audioTimestamp > 1.seconds().toMillis()) {
          audioTimestampFix += audioTimestamp - fixedTimestamp + 33 // todo use 1 frame time
        }
        tag.timestamp += audioTimestampFix
        audioTimestamp = tag.timestamp
      }
      TagType.VIDEO -> {
        val fixedTimestamp = tag.timestamp + videoTimestampFix
        if (fixedTimestamp < videoTimestamp || fixedTimestamp - videoTimestamp > 1.seconds().toMillis()) {
          videoTimestampFix += videoTimestamp - fixedTimestamp + 33 // todo use 1 frame time
        }
        tag.timestamp += videoTimestampFix
        videoTimestamp = tag.timestamp
      }
      TagType.SCRIPT -> {
      }
    }
  }

  private suspend fun getTagBody(tag: FlvTag) {
    if (dataBuf.capacity < tag.bodySize) {
      dataBuf.resize(tag.bodySize)
    }
    while (dataBuf.readable < tag.bodySize) {
      dataBuf.reset()
      if (recvData.readable != 0) {
        recvData.writeTo(dataBuf)
      } else {
        recvData.close()
        recvData = dataChannel.receive()
        recvData.writeTo(dataBuf)
      }
    }
    tag.body = dataBuf.slice(dataBuf.readPosition, tag.bodySize, 0, tag.bodySize)
    dataBuf.skip(tag.bodySize)
    filePosition += tag.bodySize
  }

  private suspend fun writeTag(tag: FlvTag) {
    val tagSize = FlvTag.TAG_HEADER_SIZE + tag.bodySize
    if (writeBuf.writeable < tagSize) {
      writeBuf.close()
      writeBuf = if (tagSize > memoryPool.blockSize) {
        HeapByteBuffer(tagSize)
      } else {
        memoryPool.get()
      }
    }
    tag.writeTag(writeBuf)
    tag.body!!.writeTo(writeBuf)
    val provideBuf = writeBuf.slice(
      writeBuf.readPosition,
      tagSize,
      writePosition = tagSize
    )
    if (resultFrameCache != null) {
      resultChannel.send(resultFrameCache!!)
    }
    resultFrameCache = provideBuf
    writeBuf.readPosition += tagSize
  }

  override suspend fun put(buffer: ByteBuffer) {
    dataChannel.send(buffer)
  }

  override suspend fun read(): ByteBuffer = resultChannel.receive()

  enum class TagType(val code: Int) {
    AUDIO(0x08), VIDEO(0x09), SCRIPT(0x12);

    companion object {
      fun valueOf(code: Int): TagType? = when (code) {
        0x08 -> AUDIO
        0x09 -> VIDEO
        0x12 -> SCRIPT
        else -> null
      }
    }
  }

  data class FlvTag(
    var prevSize: Int,
    var type: TagType,
    var bodySize: Int,
    var timestamp: Int,
    var streamId: Int,
    var body: ByteBuffer? = null,
  ) {
    companion object {
      const val TAG_HEADER_SIZE = 15

      operator fun invoke(buf: ByteBuffer): FlvTag? {
        val prevSize = buf.getInt(ByteOrder.BIG_ENDIAN)

        val type = buf.get().toInt()
        val tagType = TagType.valueOf(type) ?: return null

        val bodySize = buf.getIntWithSize(3, ByteOrder.BIG_ENDIAN)
        if (bodySize > 512 * 1024 || bodySize < 0) {
          buf.readPosition -= 3
          return null
        }

        val timestamp = buf.getIntWithSize(3, ByteOrder.BIG_ENDIAN) or (buf.get().toInt() shl 24) and 0x7fff_ffff

        val streamId = buf.getIntWithSize(3, ByteOrder.BIG_ENDIAN)
        if (streamId != 0) {
          buf.readPosition -= 9
          return null
        }

        return FlvTag(
          prevSize,
          tagType,
          bodySize,
          timestamp,
          streamId
        )
      }
    }

    fun writeTag(buf: ByteBuffer) {
      if (buf.writeable < TAG_HEADER_SIZE) {
        throw IndexOutOfBoundsException()
      }
      buf.putInt(prevSize, ByteOrder.BIG_ENDIAN)
      buf.putByte(type.code.toByte())
      buf.putIntWithSize(bodySize, 3, ByteOrder.BIG_ENDIAN)
      buf.putIntWithSize(timestamp, 3, ByteOrder.BIG_ENDIAN)
      buf.putByte((timestamp shr 24).toByte())
      buf.putIntWithSize(streamId, 3, ByteOrder.BIG_ENDIAN)
    }
  }
}

