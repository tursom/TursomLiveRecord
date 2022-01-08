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
  private val channel: Channel<Mail> = Channel(128),
) : MailContext {
  companion object : Slf4jImpl()

  init {
    repeat(max(Runtime.getRuntime().availableProcessors(), 4)) {
      GlobalScope.launch(Dispatchers.IO) {
        while (true) try {
          channel.receive().send()
        } catch (e: Exception) {
          logger.error("an exception caused on send mail", e)
        }
      }
    }
  }

  override fun sendMailBlocking(mail: Mail) {
    runBlocking {
      sendMail(mail)
    }
  }

  override suspend fun sendMail(mail: Mail) {
    channel.trySend(mail)
  }
}