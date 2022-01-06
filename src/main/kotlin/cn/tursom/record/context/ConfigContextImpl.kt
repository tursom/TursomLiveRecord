package cn.tursom.record.context

import cn.tursom.mail.GroupEmailData
import cn.tursom.record.config.DanmuConfig
import cn.tursom.yaml.Yaml
import java.io.File

class ConfigContextImpl(
  args: Array<out String>,
) : ConfigContext {
  override val config = run {
    val configFilePath = if (args.isNotEmpty()) {
      args[0]
    } else {
      "danmu.yml"
    }
    Yaml.parse<DanmuConfig>(File(configFilePath).readText())!!
  }
  override val baseEmailData: GroupEmailData = GroupEmailData(
    subject = "",
    host = config.mail.host,
    port = config.mail.port,
    name = config.mail.user,
    password = config.mail.password,
    from = config.mail.from ?: config.mail.user,
    to = emptyList()
  )
}