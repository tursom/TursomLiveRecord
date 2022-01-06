package cn.tursom.record.context

import cn.tursom.mail.GroupEmailData
import cn.tursom.record.config.DanmuConfig

interface ConfigContext {
  val config: DanmuConfig
  val baseEmailData: GroupEmailData
}