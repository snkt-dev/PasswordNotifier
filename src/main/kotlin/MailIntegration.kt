package snkt.org

import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import java.util.Properties

private val mailServer = System.getenv("MAIL_SERVER")
    ?: throw IllegalStateException("MAIL_SERVER env variable is not set")

private val mailServerPort = System.getenv("MAIL_SERVER_PORT")
    ?: throw IllegalStateException("MAIL_SERVER_PORT env variable is not set")

private val mailServerUser: String? = System.getenv("MAIL_USER")
    ?: throw IllegalStateException("MAIL_USER env variable is not set")

private val mailServerPassword: String? = System.getenv("MAIL_USER_PASSWORD")

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
    Transport.send(message)
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