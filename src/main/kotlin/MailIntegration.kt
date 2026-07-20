package snkt.org

import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.MessagingException
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import java.net.ConnectException
import java.util.Properties

private val mailServer = _mailHost
    ?: throw IllegalStateException("'--mail_host' variable is not set")

private val mailServerPort = _mailPort
    ?: throw IllegalStateException("'--mail_port' variable is not set")

private val mailServerUser: String = _mailUser
    ?: throw IllegalStateException("'--mail_user' variable is not set")

private val mailServerPassword: String? = _mailPass.also {
    if (it == null) logger.warn { "'--mail_pass' variable is not set" }
}

private const val MAX_ATTEMPTS = 3

fun sendMail(session: Session, to: String, subject: String, payload: String) {

    logger.debug { "Composing message..." }
    val message = MimeMessage(session).apply {
        setFrom(InternetAddress(mailServerUser))
        setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
        setSubject(subject, "UTF-8")

        val htmlDoc = MimeBodyPart().apply {
            setContent(payload, "text/html; charset=UTF-8")
        }

        setContent(MimeMultipart().apply {
            addBodyPart(htmlDoc)
        })
    }

    logger.debug { "Sending message to $to" }
    for (attempt in 1..MAX_ATTEMPTS) {
        try {
            Transport.send(message)
            logger.debug { "Message sent" }
            return
        } catch (e: MessagingException) {
            if (attempt == MAX_ATTEMPTS) {
                throw RuntimeException("Failed to send email to $to after $attempt attempt(s)", e)
            }
            logger.warn(e) { "Error sending message to $to, attempt $attempt/$MAX_ATTEMPTS" }
            Thread.sleep(attempt * 1000L)
        }
    }

    var i = 1
    while (i < 4) {
        try {
            Transport.send(message)
            i = 4
        } catch (e: ConnectException) {
            if (i == 3) {
                throw RuntimeException("Failed to send email via mail service!", e)
            }

            logger.info { "Error sending message. Attempt: $i" }
            i++
            Thread.sleep(1000)
        }
    }
    logger.debug { "Message sent" }
}

fun initMailSession(): Session {
    logger.debug { "Initializing mail session..." }
    val props = Properties().apply {
        put("mail.smtp.host", mailServer)
        put("mail.smtp.port", mailServerPort)
        put("mail.smtp.auth", mailServerPassword != null)
        put("mail.smtp.starttls.enable", "false")
        put("mail.smtp.connectiontimeout", "5000")
        put("mail.smtp.timeout", "5000")
        put("mail.smtp.writetimeout", "5000")
//        put("mail.debug", "true")
    }

    if (mailServerPassword != null) {
        logger.debug { "Adding password to your mail session..." }
        return Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() =
                PasswordAuthentication(mailServerUser, mailServerPassword)
        })
    }
    return Session.getInstance(props)
}