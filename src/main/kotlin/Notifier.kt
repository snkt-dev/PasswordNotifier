package snkt.org

import snkt.org.PasswordNotifier.BuildConfig
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.io.path.Path
import kotlin.time.toKotlinInstant

private val expiryTriggerDays = _triggerDays
    ?: throw IllegalStateException("'--trigger_days' variable is not set")

private val adDomain = _adDomain
    ?: throw IllegalStateException("'--ad_domain' variable is not set")

private val htmlTemplate = Files.readString(Path("index.html"))
    ?: FileNotFoundException("The file 'index.html' was not found. You should add it to the executable folder.")

//private val timezone = System.getenv("TIMEZONE")
//    ?: throw IllegalStateException("TIMEZONE variable is not set (You can get more info using command: ${BuildConfig.COMMAND_NAME} --timezones)")

fun notifyAllUsersWhosePasswordsAboutToExpire() {
    val users = fetchAllUsers()
    val mailSession = initMailSession()

    users.filter { user ->
        if (user["passwordExpiryDate"] == "0") return@filter false

        logger.debug { "Filtering ${user["name"]} - ${user["passwordExpiryDate"]}" }
        Instant.now()
            .plus(Duration.ofDays(expiryTriggerDays))
            .isAfter((user["passwordExpiryDate"] as Instant))
    }.forEach { user ->
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
}