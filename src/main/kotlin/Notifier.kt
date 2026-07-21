package snkt.org

import java.io.FileNotFoundException
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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

private val pool = Executors.newFixedThreadPool(mailingThreadCount)

fun notifyAllUsersWhosePasswordsAboutToExpire() {
    val users = fetchAllUsers()
    val mailSession = initMailSession()

    val filtered = users.filter { user ->
        if (user["passwordExpiryDate"] == "0") return@filter false

        logger.debug { "Filtering ${user["name"]} - ${user["passwordExpiryDate"]}" }
        Instant.now()
            .plus(Duration.ofDays(expiryTriggerDays))
            .isAfter((user["passwordExpiryDate"] as Instant))
    }.toList()

    if (!onlyAdminReportMode) {
        filtered.forEach { user ->
            try {
                pool.execute {
                    logger.debug { "Sending mail to ${user["email"]}" }
                    sendMail(
                        session = mailSession,
                        to = user["email"].toString(),
                        subject = "Ваш пароль аккаунта Windows скоро истечет!",
                        payload = createHtmlDoc(
                            htmlTemplate as String,
                            mapOf(
                                "Name" to user["name"].toString(),
                                "PasswordExpiryDate" to ZonedDateTime
                                    .parse(user["passwordExpiryDate"].toString())
                                    .toLocalDate()
                                    .toString(),
                                "Domain" to adDomain
                            )
                        )
                    )
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to send mail to ${user["email"]}" }
            }
        }
    }

    generateExpiryUsersAdminReport(mailSession, filtered)
}

fun shutdownMailingPool() {
    pool.shutdown()
    if (!pool.awaitTermination(5, TimeUnit.MINUTES)) {
        logger.error { "Pool didn't terminate in time" }
        pool.shutdownNow()
    }
}