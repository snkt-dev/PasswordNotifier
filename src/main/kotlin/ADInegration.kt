package snkt.org

import com.unboundid.ldap.sdk.Filter
import com.unboundid.ldap.sdk.LDAPConnection
import com.unboundid.ldap.sdk.LDAPException
import com.unboundid.ldap.sdk.SearchRequest
import com.unboundid.ldap.sdk.SearchResult
import com.unboundid.ldap.sdk.SearchScope
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.io.path.Path

private val adHost = _adHost
    ?: throw IllegalStateException("'--ad_host' variable is not set")

private val adPort = _adPort
    ?: throw IllegalStateException("'--ad_port' variable is not set")

private val adUser = _adUser
    ?: throw IllegalStateException("'--ad_user' variable is not set")

private val adPassword = _adPass
    ?: throw IllegalStateException("'--ad_pass' variable is not set")

private val adExcludedGroup: String? = _adExGroup.also {
    if (it == null) logger.warn { "'--ad_ex_group' variable is not set. Example: '--ad_ex_group !ADMINS'" }
}

private val adUserPath = _adUserPath
    ?: throw IllegalStateException("'--ad_user_path' variable is not set. Example: '--ad_user_path CN=Users,DC=snkt,DC=dev'")

private val adminMailAddress = _adminMailAddress
    ?: throw IllegalStateException("'--admin_mail_address' variable is not set")

private val adminHtmlTemplate = Files.readString(Path("admin_mail.html"))
    ?: FileNotFoundException("The file 'admin_mail.html' was not found. You should add it to the executable folder.")

private const val MAX_ATTEMPTS = 3

fun fetchAllUsers(): List<Map<String, Any>> {
    logger.debug { "Connecting to LDAP..." }
    var conn: LDAPConnection? = null
    for (attempt in 1..MAX_ATTEMPTS) {
        try {
            conn = LDAPConnection(
                adHost,
                adPort,
                adUser,
                adPassword
            )
        } catch (e: LDAPException) {
            if (attempt == MAX_ATTEMPTS) {
                sendAdminMail(attempt, e)
                throw RuntimeException("Failed to connect to $adHost:$adPort in $attempt attempts")
            }
            logger.warn(e) { "Failed to connect to $adHost:$adPort. Attempt $attempt/$MAX_ATTEMPTS" }
            Thread.sleep(attempt * 1000L)
        }
    }

    val search = SearchRequest(
        adUserPath,
        SearchScope.SUB,
        Filter.create("(&(objectClass=user)(objectCategory=person)${
            if (adExcludedGroup != null) "(!(memberOf=$adExcludedGroup))" else ""
        })"),
        "displayName", "mail", "msDS-UserPasswordExpiryTimeComputed"
    )

    logger.debug { "Starting user fetching..." }
    var result: SearchResult? = null
    for (attempt in 1..MAX_ATTEMPTS) {
        try {
            result = conn!!.search(search)
            logger.debug { "Fetching users completed." }
            break
        } catch (e: Exception) {
            if (attempt == MAX_ATTEMPTS) {
                sendAdminMail(attempt, e)
                throw RuntimeException("Failed to fetch users after $attempt attempts", e)
            }
            logger.warn(e) { "Error fetching users attempt $attempt/$MAX_ATTEMPTS" }
            Thread.sleep(attempt * 1000L)
        } finally {
            conn!!.close()
        }
    }

    return result!!.searchEntries.filter { e ->
        logger.debug { "User check: ${e.getAttributeValue("displayName")}" }
        val ex = e.getAttributeValue("displayName") != null
                && e.getAttributeValue("mail") != null
                && e.getAttributeValue("msDS-UserPasswordExpiryTimeComputed") != null
        logger.debug { "User check result: $ex" }
        ex
    }.map { e ->
        val msPasswordExpiryDate: String? = e.getAttributeValue("msDS-UserPasswordExpiryTimeComputed")

        mapOf(
            "name" to (e.getAttributeValue("displayName")),
            "email" to (e.getAttributeValue("mail")),
            "passwordExpiryDate" to ((msPasswordExpiryDate?.toLong()?.let { fileTimeToInstant(it) }) ?: "0")
        )
    }
}

private fun fileTimeToInstant(fileTime: Long): Instant? {
    if (fileTime == 0L || fileTime == 0x7FFFFFFFFFFFFFFFL) return null
    val epochDiff = 11644473600L // секунды между 1601 и 1970
    val seconds = fileTime / 10_000_000 - epochDiff
    return Instant.ofEpochSecond(seconds)
}

private fun sendAdminMail(attempt: Int, e: Exception) {
    logger.debug { "Sending report to admin..." }
    sendMail(
        initMailSession(),
        adminMailAddress,
        "Password notifier service error",
        createHtmlDoc(adminHtmlTemplate as String,
            mapOf(
                "FailedAt" to LocalDateTime.now().toString(),
                "ADHost" to adHost,
                "ADPort" to adPort.toString(),
                "ADUser" to adUser,
                "Attempts" to attempt.toString(),
                "ErrorMessage" to e.stackTraceToString()
            ))
    )
    logger.debug { "Admin report sent" }
}