package cn.tursom.record.context

import cn.tursom.mail.Mail

interface MailContext {
  fun sendMailBlocking(mail: Mail, callback: ((Exception?) -> Unit)? = null)
  suspend fun sendMail(mail: Mail, callback: ((Exception?) -> Unit)? = null)
}