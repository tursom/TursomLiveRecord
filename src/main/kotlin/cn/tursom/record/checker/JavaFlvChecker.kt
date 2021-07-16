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
import kotlin.system.exitProcess

class JavaFlvChecker : FlvChecker {
  companion object : Slf4jImpl() {
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
  private val memoryPool = HeapMemoryPool(512 * 1024, 4)
  private var writeBuf = memoryPool.get()
  private val coroutineScope = CoroutineScope(EmptyCoroutineContext)
  private val dataBuf = HeapByteBuffer(512 * 1024)
  private var videoTimestamp = 0
  private var audioTimestamp = 0
  private var videoTimestampFix = 0
  private var audioTimestampFix = 0

  init {
    coroutineScope.launch {
      var filePosition = 0L
      var recvData = dataChannel.receive()
      recvData.writeTo(dataBuf)
      while (dataBuf.readable < 9) {
        recvData.close()
        recvData = dataChannel.receive()
        recvData.writeTo(dataBuf)
      }

      if (!isValidFlvHeader(dataBuf.slice(dataBuf.readPosition, 9, writePosition = 9))) {
        logger.error("invalid flv header")
        return@launch
      }

      dataBuf.slice(dataBuf.readPosition, 9, writePosition = 9).writeTo(writeBuf)
      resultChannel.send(writeBuf.slice(0, 9, writePosition = 9))
      dataBuf.readPosition += 9
      writeBuf.readPosition += 9
      filePosition += 9

      try {
        while (true) {
          while (dataBuf.readable < 15) {
            dataBuf.reset()
            if (recvData.readable != 0) {
              recvData.writeTo(dataBuf)
            } else {
              recvData.close()
              recvData = dataChannel.receive()
              recvData.writeTo(dataBuf)
            }
          }
          val tagData = TagData(dataBuf)
          filePosition += 15
          when (tagData.type) {
            TagType.AUDIO -> {
              val fixedTimestamp = tagData.timestamp + audioTimestampFix
              if (fixedTimestamp < audioTimestamp || fixedTimestamp - audioTimestamp > 1.seconds().toMillis()) {
                audioTimestampFix += audioTimestamp - tagData.timestamp + 10
              }
              tagData.timestamp += audioTimestampFix
              audioTimestamp = tagData.timestamp
            }
            TagType.VIDEO -> {
              val fixedTimestamp = tagData.timestamp + videoTimestampFix
              if (fixedTimestamp < videoTimestamp || fixedTimestamp - videoTimestamp > 1.seconds().toMillis()) {
                videoTimestampFix += videoTimestamp - tagData.timestamp + 33
              }
              tagData.timestamp += videoTimestampFix
              videoTimestamp = tagData.timestamp
            }
            TagType.SCRIPT -> {
            }
          }
          // logger.debug("{}({}): {}", filePosition.toHexString(), filePosition, tagData)

          while (dataBuf.readable < tagData.dataSize) {
            dataBuf.reset()
            if (recvData.readable != 0) {
              recvData.writeTo(dataBuf)
            } else {
              recvData.close()
              recvData = dataChannel.receive()
              recvData.writeTo(dataBuf)
            }
          }
          tagData.body = dataBuf.slice(dataBuf.readPosition, tagData.dataSize, 0, tagData.dataSize)
          dataBuf.skip(tagData.dataSize)
          filePosition += tagData.dataSize


          if (writeBuf.writeable < 15 + tagData.dataSize) {
            writeBuf.close()
            writeBuf = if (tagData.dataSize + 15 > memoryPool.blockSize) {
              HeapByteBuffer(tagData.dataSize + 15)
            } else {
              memoryPool.get()
            }
          }
          tagData.writeTag(writeBuf)
          tagData.body!!.writeTo(writeBuf)
          val provideBuf = writeBuf.slice(
            writeBuf.readPosition,
            tagData.dataSize + 15,
            writePosition = tagData.dataSize + 15
          )
          resultChannel.send(provideBuf)
          writeBuf.readPosition += tagData.dataSize + 15

        }
      } catch (e: Exception) {
        e.printStackTrace()
        exitProcess(0)
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
    var prevSize: Int,
    var type: TagType,
    var dataSize: Int,
    var timestamp: Int,
    var streamId: Int,
    var body: ByteBuffer? = null,
  ) {
    constructor(buf: ByteBuffer) : this(
      buf.getInt(ByteOrder.BIG_ENDIAN),
      TagType.valueOf(buf.get().toInt()),
      buf.getIntWithSize(3, ByteOrder.BIG_ENDIAN),
      buf.getIntWithSize(3, ByteOrder.BIG_ENDIAN) or (buf.get().toInt() shl 24) and 0x7fff_ffff,
      buf.getIntWithSize(3, ByteOrder.BIG_ENDIAN),
    )

    fun writeTag(buf: ByteBuffer) {
      if (buf.writeable < 15) {
        throw IndexOutOfBoundsException()
      }
      buf.put(prevSize, ByteOrder.BIG_ENDIAN)
      buf.put(type.code.toByte())
      buf.putIntWithSize(dataSize, 3, ByteOrder.BIG_ENDIAN)
      buf.putIntWithSize(timestamp, 3, ByteOrder.BIG_ENDIAN)
      buf.put((timestamp shr 24).toByte())
      buf.putIntWithSize(streamId, 3, ByteOrder.BIG_ENDIAN)
    }
  }
}

