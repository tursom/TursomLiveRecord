package cn.tursom.record.context

import cn.tursom.mail.Mail

interface MailContext {
  fun sendMailBlocking(mail: Mail)
  suspend fun sendMail(mail: Mail)
}