package cn.tursom.record.context

import cn.tursom.core.coroutine.GlobalScope
import cn.tursom.core.uncheckedCast
import cn.tursom.log.impl.Slf4jImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class MsgQueueContextImpl : MsgQueueContext {
  companion object : Slf4jImpl()

  data class HandlerData<T>(
    val type: Class<T>,
    val channel: Channel<T>,
    var handler: suspend (T) -> Unit,
    var job: Job,
  )

  private val handlerMap = ConcurrentHashMap<Class<*>, HandlerData<*>>()
  private val handlerList = ConcurrentLinkedQueue<HandlerData<*>>()
  private val cannotHandlerSet = ConcurrentHashMap<Class<*>, Unit>().keySet(Unit)

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun <T : Any> registerHandler(type: Class<T>, handler: suspend (T) -> Unit) {
    synchronized(this) {
      var handlerData = handlerMap[type]
      if (handlerData != null) {
        handlerData.handler = handler.uncheckedCast()
      } else {
        val channel = Channel<T>()
        handlerData = HandlerData(type, channel, handler, GlobalScope.launch {
          while (!channel.isClosedForSend) {
            val value = channel.receive()
            try {
              handlerData!!.handler.uncheckedCast<suspend (T) -> Unit>()(value)
            } catch (e: Exception) {
              log.error("an exception caused on msg queue handle", e)
            }
          }
        })
        handlerList.add(handlerData)
        handlerMap[type] = handlerData
        cannotHandlerSet.removeIf {
          type.isAssignableFrom(it)
        }
      }
    }
  }

  override suspend fun send(msg: Any) {
    val type = msg.javaClass
    (handlerMap[type]?.channel ?: if (type in cannotHandlerSet) {
      null
    } else handlerList.firstOrNull {
      it.type.isAssignableFrom(type)
    }?.channel
      )?.uncheckedCast<Channel<Any>>()?.send(msg) ?: run {
      synchronized(this@MsgQueueContextImpl) {
        cannotHandlerSet.add(type)
      }
    }
  }
}
