package cn.tursom.record.im.handler

import cn.tursom.im.MsgSender
import cn.tursom.im.protobuf.TursomSystemMsg
import cn.tursom.record.context.GlobalContext
import cn.tursom.record.listenRoom
import com.google.protobuf.ByteString
import java.io.File

@Suppress("unused")
class ImRequestHandler(
  private val globalContext: GlobalContext,
) {
  suspend fun TursomSystemMsg.ListenLiveRoom.listenLiveRoom(msgSender: MsgSender) {
    val roomId = roomId.toInt()
    val liver = liver
    val sender = msgSender.sender
    if (globalContext.dbContext.addLiveRoom(roomId, liver)) {
      globalContext.dbContext.addUserListenRoom(sender, roomId)
      listenRoom(globalContext, roomId, liver)
    }
  }

  suspend fun TursomSystemMsg.GetLiveDanmuRecordList.getLiveDanmuRecordList(msgSender: MsgSender) {
    val recordList = globalContext.dbContext.listLiveRecordFileByRoomId(
      roomId.toInt(),
      skip,
      limit
    )
    val response = TursomSystemMsg.ReturnLiveDanmuRecordList.newBuilder()
      .setReqId(reqId)
      .setRoomId(roomId)
      .addAllRecordList(recordList.map {
        TursomSystemMsg.LiveDanmuRecord.newBuilder()
          .setId(it.id)
          .setStart(it.startTime)
          .setStop(it.endTime)
          .setSize(File("rec/" + it.recFile).length())
          .build()
      })
      .build()
    msgSender.send(response)
  }

  suspend fun TursomSystemMsg.GetLiveDanmuRecord.getLiveDanmuRecord(msgSender: MsgSender) {
    val roomLiveRecord = globalContext.dbContext.getLiveRecordFileById(liveDanmuRecordId)
    val recFile = roomLiveRecord?.let {
      File("rec/" + it.recFile)
    }
    val response = TursomSystemMsg.ReturnLiveDanmuRecord.newBuilder()
      .setExist(recFile?.exists() ?: false)
      .setReqId(reqId)

    if (recFile != null && recFile.exists()) {
      response.data = ByteString.readFrom(recFile.inputStream())
    }
    msgSender.send(response.build())
  }

  suspend fun TursomSystemMsg.GetServiceRequest.getServiceRequest(msgSender: MsgSender) {
    val response = TursomSystemMsg.GetServiceResponse.newBuilder()
      .setReqId(reqId)

    val systemHandler = msgSender.client.handler.system.handlerTypeUrlSet
    val broadcastHandler = msgSender.client.handler.broadcast.handlerTypeUrlSet

    if (serviceIdList.isEmpty()) {
      response.addAllSystemServiceId(systemHandler)
      response.addAllBroadServiceId(broadcastHandler)
    } else {
      response.addAllSystemServiceId(serviceIdList.filter { it in systemHandler })
      response.addAllBroadServiceId(serviceIdList.filter { it in broadcastHandler })
    }

    msgSender.send(response.build())
  }
}
