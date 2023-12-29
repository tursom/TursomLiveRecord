package cn.tursom.record.context

import cn.tursom.core.coroutine.GlobalScope
import cn.tursom.core.coroutine.SingletonCoroutine
import cn.tursom.core.util.seconds
import cn.tursom.im.ImWebSocketClient
import cn.tursom.im.ImWebSocketClientCoroutineContext
import cn.tursom.im.connectAndWait
import cn.tursom.record.config.ImConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class ImContextImpl(override val imConfig: ImConfig) : ImContext {
  private val reconnector = SingletonCoroutine {
    val client = this.coroutineContext[ImWebSocketClientCoroutineContext.Key]?.client ?: return@SingletonCoroutine

    while (client.closed || !client.login) {
      if (!client.onOpen && !client.onLogin) {
        client.open()
      }
      delay(10.seconds().toMillis())
    }
  }

  override lateinit var imUserId: String
    private set

  override val im = connectAndWait(imConfig.server, imConfig.token) { client, receiveMsg ->
    println("im connected")
    imUserId = receiveMsg.loginResult.imUserId
    listenBroadcast(client)
    client.handler.onClose {
      println("im connection closed")
      reconnector.run(ImWebSocketClientCoroutineContext(client))
    }
  }

  private suspend fun listenBroadcast(client: ImWebSocketClient) {
    val channel = imConfig.channel ?: 12345
    while (!client.listenBroadcast(channel)) {
      delay(100)
    }
    println("listen channel $channel")
  }
}
