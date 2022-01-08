package cn.tursom.record.context

import org.ktorm.database.Database

class GlobalContext(
  args: Array<out String>,
) {
  val mailContext: MailContext = MailContextImpl()
  val msgQueue: MsgQueueContext = MsgQueueContextImpl()
  val configContext: ConfigContext = ConfigContextImpl(args)
  val dbContext: DBContext = KtormContextImpl(Database.connect(
    "jdbc:sqlite:danmu.db",
    "org.sqlite.JDBC"
  ))
  val imContext: ImContext? = configContext.config.im?.let(::ImContextImpl)
  val roomContext: RoomContext = RoomContextImpl(this)
}
