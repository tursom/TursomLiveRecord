package cn.tursom.record

import cn.tursom.im.ImWebSocketClient
import cn.tursom.im.protobuf.TursomMsg
import cn.tursom.im.protobuf.TursomSystemMsg


suspend fun main() {
  val imWebSocketClient = ImWebSocketClient(
    "ws://127.0.0.1:12345/ws",
    "CNeb25i9srXUchILMjFiNjg2YUIzejY="
  )
  imWebSocketClient.handler.addListener(imWebSocketClient.handler::onOpen) { _ ->
    println("client opened")
  }

  imWebSocketClient.handler.handleMsg(TursomMsg.ImMsg.ContentCase.LOGINRESULT) { client, receiveMsg ->
    println("user ${client.currentUserId} login")
    imWebSocketClient.listenBroadcast(123)
  }

  imWebSocketClient.handler.broadcast.handle(TursomMsg.MsgContent.ContentCase.MSG) { client, receiveMsg ->
    println("receive broadcast msg: ${receiveMsg.broadcast.content.msg}")
    client.close()
  }

  imWebSocketClient.handler.broadcast.registerListenLiveRoomHandler { _, receiveMsg, listenLiveRoom ->
    // TODO
  }

  imWebSocketClient.handler.broadcast.registerGetLiveDanmuRecordListHandler { client, receiveMsg, listenLiveRoom ->
    client.sendExtMsg(receiveMsg.broadcast.sender, TursomSystemMsg.ReturnLiveDanmuRecordList.newBuilder()
      .setReqId(listenLiveRoom.reqId)
      .setRoomId(listenLiveRoom.roomId)
    )
    // client.sendBroadcast(receiveMsg.broadcast.channel, TursomSystemMsg.ReturnLiveDanmuRecordList.newBuilder()
    //   .setReqId(listenLiveRoom.reqId)
    //   .setRoomId(listenLiveRoom.roomId)
    // )
    // TODO
  }

  imWebSocketClient.handler.broadcast.registerGetLiveDanmuRecordHandler { client, receiveMsg, listenLiveRoom ->
    // TODO
  }

  imWebSocketClient.open()
  imWebSocketClient.waitClose()
  println("closed")
}