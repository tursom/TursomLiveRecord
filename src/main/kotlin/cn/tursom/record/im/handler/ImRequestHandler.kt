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
  suspend fun listenLiveRoom(
    listenLiveRoom: TursomSystemMsg.ListenLiveRoom,
    msgSender: MsgSender,
  ) {
    val roomId = listenLiveRoom.roomId.toInt()
    val liver = listenLiveRoom.liver
    val sender = msgSender.sender
    if (globalContext.dbContext.addLiveRoom(roomId, liver)) {
      globalContext.dbContext.addUserListenRoom(sender, roomId)
      listenRoom(globalContext, roomId, liver)
    }
  }

  suspend fun getLiveDanmuRecordList(
    getLiveDanmuRecordList: TursomSystemMsg.GetLiveDanmuRecordList,
    msgSender: MsgSender,
  ) {
    val recordList = globalContext.dbContext.listLiveRecordFileByRoomId(
      getLiveDanmuRecordList.roomId.toInt(),
      getLiveDanmuRecordList.skip,
      getLiveDanmuRecordList.limit
    )
    val response = TursomSystemMsg.ReturnLiveDanmuRecordList.newBuilder()
      .setReqId(getLiveDanmuRecordList.reqId)
      .setRoomId(getLiveDanmuRecordList.roomId)
      .addAllRecordList(recordList.map {
        TursomSystemMsg.LiveDanmuRecord.newBuilder()
          .setId(it.id)
          .setStart(it.startTime)
          .setStart(it.endTime)
          .build()
      })
      .build()
    msgSender.send(response)
  }

  suspend fun getLiveDanmuRecord(
    getLiveDanmuRecord: TursomSystemMsg.GetLiveDanmuRecord,
    msgSender: MsgSender,
  ) {
    val roomLiveRecord = globalContext.dbContext.getLiveRecordFileById(getLiveDanmuRecord.liveDanmuRecordId)
    val recFile = roomLiveRecord?.let {
      File(it.recFile)
    }
    val response = TursomSystemMsg.ReturnLiveDanmuRecord.newBuilder()
      .setExist(recFile?.exists() ?: false)
      .setReqId(getLiveDanmuRecord.reqId)

    if (recFile != null && recFile.exists()) {
      response.data = ByteString.readFrom(recFile.inputStream())
    }
    msgSender.send(response.build())
  }

  suspend fun getServiceRequest(
    getServiceRequest: TursomSystemMsg.GetServiceRequest,
    msgSender: MsgSender,
  ) {
    val response = TursomSystemMsg.GetServiceResponse.newBuilder()
      .setReqId(getServiceRequest.reqId)

    val systemHandler = msgSender.client.handler.system.handlerTypeUrlSet
    val broadcastHandler = msgSender.client.handler.broadcast.handlerTypeUrlSet

    if (getServiceRequest.serviceIdList.isEmpty()) {
      response.addAllSystemServiceId(systemHandler)
      response.addAllBroadServiceId(broadcastHandler)
    } else {
      response.addAllSystemServiceId(getServiceRequest.serviceIdList.filter { it in systemHandler })
      response.addAllBroadServiceId(getServiceRequest.serviceIdList.filter { it in broadcastHandler })
    }

    msgSender.send(response.build())
  }
}
