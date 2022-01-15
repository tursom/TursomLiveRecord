package cn.tursom.record.context

import cn.tursom.database.ktorm.*
import cn.tursom.record.entity.Room
import cn.tursom.record.entity.RoomLiveRecord
import cn.tursom.record.entity.User
import cn.tursom.record.entity.UserRoom
import org.ktorm.database.Database
import org.ktorm.dsl.and
import org.ktorm.dsl.limit
import org.ktorm.dsl.mapNotNull
import org.ktorm.dsl.select
import org.ktorm.dsl.where
import org.sqlite.SQLiteErrorCode
import org.sqlite.SQLiteException

class KtormContextImpl(
  val idContext: IdContext,
  val database: Database,
) : DBContext {
  init {
    database.useConnection { conn ->
      val statement = conn.createStatement()
      statement.executeUpdate(
        "create table if not exists room(" +
          "room_id int primary key not null," +
          "liver text," +
          "rec_file text" +
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
      statement.executeUpdate(
        "create table if not exists room_live_record(" +
          "id text primary key not null," +
          "room_id int not null," +
          "rec_file text not null," +
          "start_time long not null," +
          "end_time long not null" +
          ")"
      )
      statement.close()
    }
  }

  override var userRoomMap: Map<String, List<Int>> = listUserRoom()
    .groupBy {
      it.uid
    }
    .mapValues {
      it.value.map(UserRoom::roomId)
    }
    private set

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

  override fun listUsersById(id: Collection<String>): List<User> {
    return database.from<User>()
      .select()
      .where {
        User::uid inList id
      }
      .toList()
  }

  override fun addUserListenRoom(uid: String, roomId: Int): Boolean {
    return database.insert(UserRoom(uid, roomId)) != 0
  }

  override fun removeUserListenRoom(uid: String, roomId: Int): Boolean {
    return database.delete<UserRoom> {
      (UserRoom::uid eq uid) and (UserRoom::roomId eq roomId)
    } != 0
  }

  override fun listUserRoom(): List<UserRoom> {
    return database.from<UserRoom>()
      .select()
      .toList()
  }

  override fun listListenRoomUsers(roomId: Int): List<String> {
    return database.from<UserRoom>()
      .select()
      .where {
        UserRoom::roomId eq roomId
      }
      .mapNotNull {
        it[UserRoom::uid]
      }
  }

  override fun listListenRoomUsersMail(roomId: Int): List<String> {
    return listUsersById(listListenRoomUsers(roomId)).mapNotNull(User::mail)
  }

  override fun listRoom(): List<Room> {
    return database.from<Room>()
      .select()
      .toList()
  }

  override fun addLiveRoom(roomId: Int, liver: String?): Boolean {
    return try {
      database.insert(Room(
        roomId = roomId,
        liver = liver ?: roomId.toString(),
      )) != 0
    } catch (e: SQLiteException) {
      if (e.resultCode == SQLiteErrorCode.SQLITE_CONSTRAINT_PRIMARYKEY) {
        true
      } else {
        throw e
      }
    }
  }

  override fun getLiveRoomById(roomId: Int): Room? {
    return database.from<Room>()
      .select()
      .where {
        Room::roomId eq roomId
      }
      .getOne()
  }

  override fun getRoomRecFile(roomId: Int): String? {
    return database.from<Room>()
      .select()
      .where {
        Room::roomId eq roomId
      }.getOne()
  }

  override fun setRoomRecFile(roomId: Int, recFile: String): Boolean {
    return database.update<Room> {
      set(Room::recFile, recFile)
      where {
        Room::roomId eq roomId
      }
    } != 0
  }

  override fun recordLiveRecordFile(roomId: Int, recFile: String, startTime: Long, endTime: Long) {
    database.insert(RoomLiveRecord(idContext.id(), roomId, recFile, startTime, endTime))
  }

  override fun listLiveRecordFileByRoomId(roomId: Int, skip: Int, limit: Int): List<RoomLiveRecord> {
    return database.from<RoomLiveRecord>()
      .select()
      .where {
        RoomLiveRecord::roomId eq roomId
      }
      .limit(skip, limit)
      .toList()
  }

  override fun getLiveRecordFileById(id: String): RoomLiveRecord? {
    return database.from<RoomLiveRecord>()
      .select()
      .where {
        RoomLiveRecord::id eq id
      }.getOne()
  }
}
