package cn.tursom.record.context

import cn.tursom.core.coroutine.GlobalScope
import cn.tursom.log.impl.Slf4jImpl
import cn.tursom.mail.Mail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.max

class MailContextImpl(
  private val channel: Channel<MailData> = Channel(128),
) : MailContext {
  companion object : Slf4jImpl()
  data class MailData(
    val mail: Mail,
    val callback: ((Exception?) -> Unit)?,
  )

  init {
    repeat(max(Runtime.getRuntime().availableProcessors(), 4)) {
      GlobalScope.launch(Dispatchers.IO) {
        while (true) {
          var exception: Exception? = null
          val (mail, callback) = channel.receive()
          try {
            mail.send()
          } catch (e: Exception) {
            logger.error("an exception caused on send mail", e)
            exception = e
          } finally {
            callback?.invoke(exception)
          }
        }
      }
    }
  }

  override fun sendMailBlocking(mail: Mail, callback: ((Exception?) -> Unit)?) {
    runBlocking {
      sendMail(mail, callback)
    }
  }

  override suspend fun sendMail(mail: Mail, callback: ((Exception?) -> Unit)?) {
    channel.trySend(MailData(mail, callback))
  }
}