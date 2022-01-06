package cn.tursom.record.context

import cn.tursom.record.entity.Room
import cn.tursom.record.entity.User
import cn.tursom.record.entity.UserRoom

interface DBContext {
  fun findUserById(uid: String): User?
  fun listUsers(): List<User>

  fun listUserRoom(): List<UserRoom>

  fun listRoom(): List<Room>
  fun addLiveRoom(roomId: Int, liver: String?): Boolean
  fun getLiveRoomById(roomId: Int): Room?
}
