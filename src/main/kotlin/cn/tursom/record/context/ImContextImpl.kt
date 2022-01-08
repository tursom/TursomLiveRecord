package cn.tursom.record.context

import cn.tursom.im.connectAndWait
import cn.tursom.record.config.ImConfig

class ImContextImpl(override val imConfig: ImConfig) : ImContext {
  override lateinit var imUserId: String
    private set
  override val im = connectAndWait(imConfig.server, imConfig.token) { client, receiveMsg ->
    imUserId = receiveMsg.loginResult.imUserId
  }
}
