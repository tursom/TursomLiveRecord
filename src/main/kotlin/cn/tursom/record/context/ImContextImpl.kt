package cn.tursom.record.context

import cn.tursom.core.coroutine.GlobalScope
import cn.tursom.core.seconds
import cn.tursom.im.ImWebSocketClient
import cn.tursom.im.connectAndWait
import cn.tursom.record.config.ImConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class ImContextImpl(override val imConfig: ImConfig) : ImContext {
  override lateinit var imUserId: String
    private set
  private val reconnect = AtomicBoolean()
  override val im = connectAndWait(imConfig.server, imConfig.token) { client, receiveMsg ->
    imUserId = receiveMsg.loginResult.imUserId

    client.handler.onClose {
      println("im connection closed")
      if (reconnect.compareAndSet(false, true)) {
        GlobalScope.launch {
          while (client.closed) {
            delay(10.seconds().toMillis())
            client.open()
          }
          reconnect.set(false)
        }
      }
    }
    listenBroadcast(client)
  }

  private suspend fun listenBroadcast(client: ImWebSocketClient) {
    val channel = imConfig.channel ?: 12345
    while (!client.listenBroadcast(channel)) {
      delay(100)
    }
    println("listen channel $channel")
  }
}
