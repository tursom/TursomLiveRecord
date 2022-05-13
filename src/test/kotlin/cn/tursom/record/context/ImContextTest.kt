package cn.tursom.record.context

import cn.tursom.core.ThreadLocalSimpleDateFormat
import cn.tursom.core.buffer.impl.HeapByteBuffer
import cn.tursom.core.seconds
import cn.tursom.core.ungz
import cn.tursom.im.MsgSender
import cn.tursom.im.protobuf.TursomSystemMsg
import cn.tursom.record.Record
import cn.tursom.record.setDefaultTextFormatPrinter
import cn.tursom.record.util.loopRecord
import com.google.protobuf.TextFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class ImContextTest {
  private val globalContext = GlobalContext(emptyArray())

  init {
    setDefaultTextFormatPrinter(TextFormat.printer().escapingNonAscii(false))
  }

  @Test
  fun testGetServiceRequest() {
    val client = globalContext.imContext!!.im
    val channel = globalContext.configContext.config.im!!.channel ?: 12345
    runBlocking {
      client.handler.broadcast.registerHandler { unpackMsg: TursomSystemMsg.GetServiceResponse, msgSender: MsgSender ->
        println(msgSender.sender)
        println(unpackMsg)
      }

      println(client.sendBroadcast(channel, TursomSystemMsg.GetServiceRequest.newBuilder()
        .setReqId(globalContext.idContext.id())))

      delay(10.seconds().toMillis())
    }
  }

  @Test
  fun testGetLiveDanmuRecordList() {
    val client = globalContext.imContext!!.im
    //client.handler.handleMsg(TursomMsg.ImMsg.ContentCase.CHATMSG) { _, receiveMsg ->
    //  println(receiveMsg)
    //}
    client.handler.system.registerHandler { unpackMsg: TursomSystemMsg.ReturnLiveDanmuRecordList, msgSender: MsgSender ->
      println(msgSender.sender)
      println(unpackMsg)
    }
    client.sendExtMsg("21tsfd1rQ5N", TursomSystemMsg.GetLiveDanmuRecordList.newBuilder()
      .setRoomId("8584389")
      .setSkip(0)
      .setLimit(100))
    Thread.sleep(10.seconds().toMillis())
  }

  @Test
  fun testGetLiveDanmuRecord() {
    val client = globalContext.imContext!!.im
    client.handler.system.registerHandler { liveDanmuRecord: TursomSystemMsg.ReturnLiveDanmuRecord, msgSender: MsgSender ->
      println(msgSender.sender)
      HeapByteBuffer(when (liveDanmuRecord.compress) {
        TursomSystemMsg.CompressTypeEnum.GZ -> liveDanmuRecord.data.toByteArray().ungz()
        TursomSystemMsg.CompressTypeEnum.ZIP -> ZipInputStream(ByteArrayInputStream(liveDanmuRecord.data.toByteArray())).readBytes()
        else -> liveDanmuRecord.data.toByteArray()
      }).loopRecord { msg ->
        if (msg.contentCase != Record.RecordMsg.ContentCase.DANMU) {
          return@loopRecord
        }
        println("${
          ThreadLocalSimpleDateFormat.cn.format(msg.danmu.danmu.metadata.time)
        }: ${msg.danmu.danmu.userInfo.nickname}: ${msg.danmu.danmu.danmu}")
      }
    }

    client.sendExtMsg("21tsfd1rQ5N", TursomSystemMsg.GetLiveDanmuRecord.newBuilder()
      .setLiveDanmuRecordId("23DChHtbeTp"))
    Thread.sleep(10.seconds().toMillis())
  }

  @Test
  fun testListenLiveRoom() {
    val client = globalContext.imContext!!.im
    client.sendExtMsg("21tsfd1rQ5N", TursomSystemMsg.ListenLiveRoom.newBuilder()
      .setRoomId(801580)
      .setLiver("涵涵"))
  }
}