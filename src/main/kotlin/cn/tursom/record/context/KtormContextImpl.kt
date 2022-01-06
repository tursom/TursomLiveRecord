package cn.tursom.record.context

import cn.tursom.database.ktorm.*
import cn.tursom.record.entity.Room
import cn.tursom.record.entity.User
import cn.tursom.record.entity.UserRoom
import org.ktorm.database.Database
import org.ktorm.dsl.where

class KtormContextImpl(
  val database: Database,
) : DBContext {
  init {
    database.useConnection { conn ->
      val statement = conn.createStatement()
      statement.executeUpdate(
        "create table if not exists room(" +
          "room_id int primary key not null," +
          "liver text" +
          ")"
      )
      statement.executeUpdate(
        "create table if not exists user(" +
          "uid text primary key not null," +
          "mail text" +
          ")"
      )
      statement.executeUpdate(
        "create table if not exists user_room(" +
          "uid text not null," +
          "room_id text not null," +
          "unique(uid,room_id)" +
          ")"
      )
      statement.close()
    }
  }

  override fun findUserById(uid: String): User? {
    return database.from<User>()
      .select()
      .where {
        User::uid eq uid
      }
      .getOne()
  }


  override fun listUsers(): List<User> {
    return database.from<User>()
      .select()
      .toList()
  }

  override fun listUserRoom(): List<UserRoom> {
    return database.from<UserRoom>()
      .select()
      .toList()
  }

  override fun listRoom(): List<Room> {
    return database.from<Room>()
      .select()
      .toList()
  }

  override fun addLiveRoom(roomId: Int, liver: String?): Boolean {
    TODO("Not yet implemented")
  }

  override fun getLiveRoomById(roomId: Int): Room? {
    TODO("Not yet implemented")
  }

}
