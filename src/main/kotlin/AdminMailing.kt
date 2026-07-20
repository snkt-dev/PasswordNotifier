package snkt.org

import jakarta.mail.Session
import java.nio.file.Files
import java.time.LocalDateTime
import kotlin.io.path.Path

private val adminReportHtmlTemplate by lazy {
    try {
        Files.readString(Path("admin_report_mail.html"))
    } catch (e: Exception) {
        throw IllegalStateException(
            "Failed to load 'admin_report_mail.html'. Ensure it's in the executable folder",
            e
        )
    }
}

private val adminHtmlTemplate by lazy {
    try {
        Files.readString(Path("admin_mail.html"))
    } catch (e: Exception) {
        throw IllegalStateException(
            "Failed to load 'admin_mail.html'. Ensure it's in the executable folder",
            e
        )
    }
}

private val adminMailAddress = _adminMailAddress
    ?: throw IllegalStateException("'--admin_mail_address' variable is not set")


fun generateExpiryUsersAdminReport(mailSession: Session, targetUsers: List<Map<String, Any>>) {
    val startMarker = "<!--ROW:START-->"
    val endMarker = "<!--ROW:END-->"

    logger.debug { "Searching for html markers in 'admin_report_mail.html'..." }
    val parts = adminReportHtmlTemplate.split(startMarker, endMarker)
    require(parts.size == 3) { "Template must contain both $startMarker and $endMarker" }

    val (header, template, footer) = parts

    logger.debug { "Inserting user data into html: ${targetUsers.size}" }
    val buf = StringBuilder()
    buf.append(header)
    targetUsers.forEach { user ->
        buf.append(createHtmlDoc(template, user))
    }
    buf.append(footer)

    logger.debug { "Sending expiring users admin report..." }
    sendMail(
        mailSession,
        adminMailAddress,
        "Expiring users report",
        buf.toString()
    )
    logger.debug { "Done" }
}

fun generateErrorAdminReport(
    mailSession: Session,
    adHost: String,
    adPort: Int,
    adUser: String,
    attempt: Int,
    e: Exception
) {
    logger.debug { "Sending report to admin..." }
    sendMail(
        mailSession,
        adminMailAddress,
        "Password notifier service error",
        createHtmlDoc(
            adminHtmlTemplate as String,
            mapOf(
                "FailedAt" to LocalDateTime.now().toString(),
                "ADHost" to adHost,
                "ADPort" to adPort.toString(),
                "ADUser" to adUser,
                "Attempts" to attempt.toString(),
                "ErrorMessage" to e.stackTraceToString()
            )
        )
    )
    logger.debug { "Admin report sent" }
}