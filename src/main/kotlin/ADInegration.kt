package snkt.org

import com.unboundid.ldap.sdk.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

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

private const val MAX_ATTEMPTS = 3

fun fetchAllUsers(): List<Map<String, String>> {
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
                generateErrorAdminReport(
                    initMailSession(),
                    adHost,
                    adPort,
                    adUser,
                    attempt,
                    e)
                throw RuntimeException("Failed to connect to $adHost:$adPort in $attempt attempts")
            }
            logger.warn(e) { "Failed to connect to $adHost:$adPort. Attempt $attempt/$MAX_ATTEMPTS" }
            Thread.sleep(attempt * 1000L)
        }
    }

    val search = SearchRequest(
        adUserPath,
        SearchScope.SUB,
        Filter.create("(&(objectClass=user)(objectCategory=person)(!(userAccountControl:1.2.840.113556.1.4.803:=2))(!(userAccountControl:1.2.840.113556.1.4.803:=65536))${
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
                generateErrorAdminReport(
                    initMailSession(),
                    adHost,
                    adPort,
                    adUser,
                    attempt,
                    e)
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
                && e.getAttributeValue("msDS-UserPasswordExpiryTimeComputed").toLong() != 0L
        logger.debug { "User check result: $ex" }
        ex
    }.map { e ->
        val msPasswordExpiryDate: String = e.getAttributeValue("msDS-UserPasswordExpiryTimeComputed")

        mapOf(
            "name" to (e.getAttributeValue("displayName")),
            "email" to (e.getAttributeValue("mail")),
            "passwordExpiryDate" to msPasswordExpiryDate.toLong().let { fileTime ->
                fileTimeToInstant(fileTime)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            },
            "daysToExpire" to msPasswordExpiryDate.toLong().let { fileTime ->
                fileTimeToInstant(fileTime).let { instant ->
                    ChronoUnit.DAYS.between(
                        LocalDate.now(),
                        instant.atZone(ZoneId.systemDefault()).toLocalDate()
                    )
                }
            }.toString()
        )
    }
}

    private fun fileTimeToInstant(fileTime: Long): Instant {
        val epochDiff = 11644473600L // секунды между 1601 и 1970
        val seconds = fileTime / 10_000_000 - epochDiff
        return Instant.ofEpochSecond(seconds)
    }