package cn.tursom.record.entity

data class RoomLiveRecord(
  var id: String,
  var roomId: Int,
  var recFile: String,
  var startTime: Long,
  var endTime: Long,
)