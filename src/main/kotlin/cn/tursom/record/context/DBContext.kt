package cn.tursom.record.context

import cn.tursom.record.entity.Room
import cn.tursom.record.entity.User
import cn.tursom.record.entity.UserRoom

interface DBContext {
  val userRoomMap: Map<String, List<Int>>

  fun findUserById(uid: String): User?
  fun listUsers(): List<User>

  fun addUserListenRoom(uid: String, roomId: Int): Boolean
  fun removeUserListenRoom(uid: String, roomId: Int): Boolean
  fun listUserRoom(): List<UserRoom>
  fun listListenRoomUsers(roomId: Int): List<String>

  fun listRoom(): List<Room>
  fun addLiveRoom(roomId: Int, liver: String?): Boolean
  fun getLiveRoomById(roomId: Int): Room?
  fun getRoomRecFile(roomId: Int): String?
  fun setRoomRecFile(roomId: Int, recFile: String): Boolean
}
