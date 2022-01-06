package cn.tursom.record.context

import cn.tursom.ws.BiliWSClient

interface LiveRoomContext {
  fun listenRoom(
    roomId: Int,
    liver: String?,
  ): BiliWSClient
}
