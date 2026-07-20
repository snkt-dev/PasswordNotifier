package snkt.org

import com.unboundid.ldap.sdk.Filter
import com.unboundid.ldap.sdk.LDAPConnection
import com.unboundid.ldap.sdk.LDAPException
import com.unboundid.ldap.sdk.SearchRequest
import com.unboundid.ldap.sdk.SearchResult
import com.unboundid.ldap.sdk.SearchScope
import java.time.Instant

private val adHost = System.getenv("AD_HOST")
    ?: throw IllegalStateException("AD_HOST env variable is not set")

private val adPort = System.getenv("AD_PORT")?.toInt()
    ?: throw IllegalStateException("AD_PORT env variable is not set")

private val adUser = System.getenv("AD_USER")
    ?: throw IllegalStateException("AD_USER env variable is not set")

private val adPassword = System.getenv("AD_PASSWORD")
    ?: throw IllegalStateException("AD_PASSWORD env variable is not set")

private val adExcludedGroup: String? = System.getenv("AD_EXCLUDED_GROUP")

private val adUserPath = System.getenv("AD_USER_PATH")
    ?: throw IllegalStateException("AD_USER_PATH env variable is not set")

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