package cn.tursom.record

import cn.tursom.core.ThreadLocalSimpleDateFormat
import cn.tursom.core.buffer.impl.HeapByteBuffer
import cn.tursom.core.notifyAll
import cn.tursom.core.wait
import cn.tursom.ws.BiliWSClient
import cn.tursom.ws.CmdEnum
import com.google.protobuf.TextFormat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import java.io.OutputStream


class DanmuRecordTest {
  class UncloseOutputStream(
    private val os: OutputStream,
  ) : OutputStream() {
    override fun write(b: Int) {
      os.write(b)
    }

    override fun write(b: ByteArray) {
      os.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
      os.write(b, off, len)
    }

    override fun flush() {
      os.flush()
    }
  }

  @Test
  fun record() {
    val os = File("ArkNights.rec").outputStream()
    val wsClient = runBlocking {
      recordDanmu(5555734) {
        os
      }
    }
    var closed = false
    var closeTime = 0
    wsClient.addCmdListener(CmdEnum.PREPARING) {
      closed = true
    }
    wsClient.addLivingListener {
      closed = false
    }
    while (!closed && closeTime < 180) {
      if (closed) {
        closeTime++
      } else {
        closeTime = 0
      }
      Thread.sleep(1000)
    }
  }

  @Test
  fun read() {
    setDefaultTextFormatPrinter(TextFormat.printer().escapingNonAscii(false))
    //val buffer = HeapByteBuffer(GZIPInputStream(File("ArkNights.recovered").inputStream()).readBytes())
    val buffer = HeapByteBuffer(File("wula-2022-01-15 21-57.rec").inputStream().readBytes())
    while (buffer.readable != 0) {
      val size = buffer.getInt()
      val bytes = try {
        buffer.getBytes(size)
      } catch (e: Exception) {
        e.printStackTrace()
        throw e
      }
      val danmuInfo = Record.RecordMsg.parseFrom(bytes)

      when (danmuInfo.contentCase) {
        Record.RecordMsg.ContentCase.DANMU -> println("${ThreadLocalSimpleDateFormat.cn.format(danmuInfo.danmu.danmu.metadata.time)} ${danmuInfo.danmu.danmu.userInfo.nickname}: ${danmuInfo.danmu.danmu.danmu}")
        //Record.RecordMsg.ContentCase.GIFT -> println("${danmuInfo.gift}")
        else -> Unit
      }
    }
  }

  @Test
  fun testConnect(): Unit = runBlocking {
    val biliWSClient = BiliWSClient(8584389, onClose = {
      notifyAll {
      }
    })
    biliWSClient.addGiftListener {
      println(it)
    }
//    biliWSClient.addDanmuListener {
//      println(it)
//    }

    biliWSClient.connect()
    biliWSClient.wait {
    }
  }
}
