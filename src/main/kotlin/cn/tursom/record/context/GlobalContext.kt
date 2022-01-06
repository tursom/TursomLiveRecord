package cn.tursom.record.context

import org.ktorm.database.Database

class GlobalContext(
  args: Array<out String>,
) {
  val msgQueue: MsgQueueContext = MsgQueueContextImpl()
  val configContext: ConfigContext = ConfigContextImpl(args)
  val dbContext: DBContext = KtormContextImpl(Database.connect(
    "jdbc:sqlite:danmu.db",
    "org.sqlite.JDBC"
  ))
}
