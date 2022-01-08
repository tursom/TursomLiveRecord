package cn.tursom.record.im.handler

import cn.tursom.im.ImWebSocketClient
import cn.tursom.im.protobuf.TursomMsg
import cn.tursom.im.protobuf.TursomSystemMsg
import cn.tursom.record.context.GlobalContext
import cn.tursom.record.listenRoom

@Suppress("unused")
class ImRequestHandler(
  private val globalContext: GlobalContext,
) {
  @Suppress("UNUSED_PARAMETER")
  suspend fun listenLiveRoom(
    client: ImWebSocketClient,
    receiveMsg: TursomMsg.ImMsg,
    listenLiveRoom: TursomSystemMsg.ListenLiveRoom,
  ) {
    val roomId = listenLiveRoom.roomId.toInt()
    val liver = listenLiveRoom.liver
    val sender = when (receiveMsg.contentCase) {
      TursomMsg.ImMsg.ContentCase.CHATMSG -> receiveMsg.chatMsg.sender
      TursomMsg.ImMsg.ContentCase.BROADCAST -> receiveMsg.broadcast.sender
      else -> null
    }
    if (globalContext.dbContext.addLiveRoom(roomId, liver)) {
      if (sender != null) {
        globalContext.dbContext.addUserListenRoom(sender, roomId)
      }
      listenRoom(globalContext, roomId, liver)
    }
  }
}