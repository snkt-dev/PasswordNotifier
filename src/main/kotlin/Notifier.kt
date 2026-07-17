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

private val expiryTriggerDays = System.getenv("EXPIRY_TRIGGER_IN_DAYS")?.toLong()
    ?: throw IllegalStateException("EXPIRY_TRIGGER_IN_DAYS env variable is not set")

private val adDomain = System.getenv("AD_DOMAIN")
    ?: throw IllegalStateException("AD_DOMAIN env variable is not set")

private val htmlTemplate = Files.readString(Path("index.html"))
    ?: FileNotFoundException("File 'index.html' not found")

//private val timezone = System.getenv("TIMEZONE")
//    ?: throw IllegalStateException("TIMEZONE env variable is not set (You can get more info using command: ${BuildConfig.COMMAND_NAME} --timezones)")

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