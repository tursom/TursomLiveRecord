package cn.tursom.record.context

interface MsgQueueContext {
  fun <T : Any> registerHandler(type: Class<T>, handler: suspend (T) -> Unit)
  suspend fun send(msg: Any)
}
