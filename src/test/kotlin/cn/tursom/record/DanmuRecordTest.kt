package cn.tursom.record

import cn.tursom.core.buffer.impl.HeapByteBuffer
import cn.tursom.core.notifyAll
import cn.tursom.core.wait
import cn.tursom.ws.BiliWSClient
import com.google.protobuf.TextFormat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream


class DanmuRecordTest {
  @Test
  fun read() {
    setDefaultTextFormatPrinter(TextFormat.printer().escapingNonAscii(false))
    val buffer = HeapByteBuffer(GZIPInputStream(File("tursom-2022-01-03 00.rec").inputStream()).readBytes())
    while (buffer.readable != 0) {
      val size = buffer.getInt()
      val bytes = buffer.getBytes(size)
      val danmuInfo = Record.RecordMsg.parseFrom(bytes)
      when (danmuInfo.contentCase) {
        Record.RecordMsg.ContentCase.DANMU -> println("${danmuInfo.danmu.danmu.userInfo.nickname}: ${danmuInfo.danmu.danmu.danmu}")
        Record.RecordMsg.ContentCase.GIFT ->  println("${danmuInfo.gift}")
        Record.RecordMsg.ContentCase.LIVESTATUS,
        Record.RecordMsg.ContentCase.CONTENT_NOT_SET, null -> Unit
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
