package snkt.org

import java.nio.file.Files
import kotlin.io.path.Path

private val expiryTriggerDays = _triggerDays
    ?: throw IllegalStateException("'--trigger_days' variable is not set")

private val adDomain = _adDomain
    ?: throw IllegalStateException("'--ad_domain' variable is not set")

private val htmlTemplate by lazy {
    try {
        Files.readString(Path("user_mail.html"))
    } catch (e: Exception) {
        throw IllegalStateException(
            "Failed to load 'user_mail.html'. Ensure it's in the executable folder",
            e
        )
    }
}

fun notifyAllUsersWhosePasswordsAboutToExpire() {
    val users = fetchAllUsers()
    val mailSession = initMailSession()

    val filtered = users.filter { user ->
        if (user["passwordExpiryDate"] == "0") return@filter false

        logger.debug { "Filtering ${user["name"]} - ${user["passwordExpiryDate"]}" }
        user["daysToExpire"]!!.toInt() <= expiryTriggerDays
    }.toList()

    if (!onlyAdminReportMode) {
        filtered.forEach { user ->
            logger.debug { "Sending mail to ${user["email"]}" }
            sendMail(
                session = mailSession,
                to = user["email"]!!,
                subject = "Ваш пароль аккаунта Windows скоро истечет!",
                payload = createHtmlDoc(
                    htmlTemplate,
                    mapOf(
                        "name" to user["name"]!!,
                        "passwordExpiryDate" to user["passwordExpiryDate"]!!,
                        "domain" to adDomain,
                        "daysToExpire" to user["daysToExpire"]!!
                    )
                )
            )
        }
    }

    generateExpiryUsersAdminReport(mailSession, filtered)
}