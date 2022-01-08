package cn.tursom.record.context

interface RoomContext {
  suspend fun listenRoom(roomId: Int, liver: String)
}