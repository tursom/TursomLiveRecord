package cn.tursom.record.context

import cn.tursom.im.ImWebSocketClient
import cn.tursom.record.config.ImConfig

interface ImContext {
  val imConfig: ImConfig
  val im: ImWebSocketClient
  val imUserId: String
}