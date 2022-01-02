package cn.tursom.record.config

import java.util.concurrent.TimeUnit

data class DanmuConfig(
  val mail: MailConfig,
  val room: List<RoomConfig>,
)

data class MailConfig(
  val send: Boolean,
  val host: String,
  val port: Int,
  val user: String,
  val password: String,
  val from: String?,
  val imap: Imap,
  val target: List<MailTarget>,
)

data class Imap(
  val host: String,
  val port: Int,
  val freq: Int,
  val timeUnit: TimeUnit,
  val ignore: List<String>,
)

data class MailTarget(
  val mail: String,
  val roomList: List<String>?,
)

data class RoomConfig(
  val roomId: Int,
  val liver: String,
)
