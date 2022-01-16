package cn.tursom.record.context

import org.ktorm.database.Database

class GlobalContext(
  args: Array<out String>,
) {

  val configContext: ConfigContext = ConfigContextImpl(args)
  val imContext: ImContext? = configContext.config.im?.let(::ImContextImpl)
  val idContext: IdContext = SnowflakeIdContextImpl {
    imContext?.im?.imSnowflake ?: SnowflakeIdContextImpl.defaultSnowflake
  }
  val mailContext: MailContext = MailContextImpl()
  val msgQueue: MsgQueueContext = MsgQueueContextImpl()
  val dbContext: DBContext = KtormContextImpl(
    idContext,
    Database.connect(
      "jdbc:sqlite:danmu.db",
      "org.sqlite.JDBC"
    ))
  val roomContext: RoomContext = RoomContextImpl(this)
}
