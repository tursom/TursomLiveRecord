package cn.tursom.record.checker

import java.io.File
import java.io.RandomAccessFile

class RafRBuffered @JvmOverloads constructor(file: File?, mode: String?, buffSize: Int = 1024 * 1024 * 4) {
  private var buffer = ByteArray(buffSize)
  private var pointer = 0
  private var validBound = 0
  private var raf = RandomAccessFile(file, mode)

  fun read(): Int {
    return if (pointer < validBound) {
      val result = buffer[pointer].toInt()
      pointer++
      result
    } else {
      validBound = raf.read(buffer)
      pointer = 1
      buffer[0].toInt()
    }
  }

  @JvmOverloads
  fun read(b: ByteArray, off: Int = 0, len: Int = b.size): Int {
    val bufferRemainSize = validBound - pointer
    // 若buffer里面内容充足，则直接从缓存读取
    return if (bufferRemainSize >= len) {
      System.arraycopy(buffer, pointer, b, off, len)
      pointer += len
      len
    } else {
      // buffer里面内容不足，先将缓存里的全部读取
      try {
        System.arraycopy(buffer, pointer, b, off, bufferRemainSize)
      } catch (e: Exception) {
        return -1
      }
      val remainToRead = len - bufferRemainSize
      // 若要读的还剩很多，直接读取，否则先读入缓存
      if (remainToRead >= buffer.size) {
        val readSize = raf.read(b, off + bufferRemainSize, remainToRead)
        flushBuf()
        bufferRemainSize + readSize
      } else {
        val readSize = flushBuf()
        if (readSize == -1) {
          return if (bufferRemainSize == 0) -1 else bufferRemainSize
        }
        if (readSize >= remainToRead) {
          System.arraycopy(buffer, 0, b, off + bufferRemainSize, remainToRead)
          pointer = remainToRead
          bufferRemainSize + remainToRead
        } else {
          // todo
          System.arraycopy(buffer, 0, b, off + bufferRemainSize, readSize)
          pointer = 0
          validBound = 0
          bufferRemainSize + readSize
        }
      }
    }
  }

  private fun flushBuf(): Int {
    val readSize = raf.read(buffer)
    pointer = 0
    if (readSize >= 0) {
      validBound = readSize
    } else {
      validBound = 0
    }
    return readSize
  }

  fun skipBytes(n: Int): Int {
    val bufferRemainSize = validBound - pointer
    // 若buffer里面内容充足，则直接从缓存跳过
    return if (bufferRemainSize >= n) {
      pointer += n
      n
    } else {
      // buffer里面内容不足，先将缓存里的全部跳过
      val remainToRead = n - bufferRemainSize
      // 若要跳过的还剩很多，直接跳过，否则先读入缓存再跳过
      if (remainToRead >= buffer.size) {
        val readSize = raf.skipBytes(remainToRead)
        pointer = 0
        validBound = 0
        bufferRemainSize + readSize
      } else {
        val readSize = raf.read(buffer)
        if (readSize >= remainToRead) {
          validBound = readSize
          pointer = remainToRead
          bufferRemainSize + remainToRead
        } else {
          pointer = 0
          validBound = 0
          bufferRemainSize + readSize
        }
      }
    }
  }


  fun seek(pos: Long) {
    val min = raf.filePointer - validBound
    val max = raf.filePointer
    if (pos in min until max) {
      pointer = (pos - min).toInt()
    } else {
      raf.seek(pos)
      pointer = 0
      validBound = 0
    }
  }


  fun close() {
    raf.close()
  }

  val filePointer: Long
    get() = raf.filePointer - validBound + pointer
}