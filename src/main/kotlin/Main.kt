package snkt.org

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.Executors

val logger = KotlinLogging.logger {}
val executor = Executors.newSingleThreadScheduledExecutor()

var _adDomain: String? = null
var _adExGroup: String? = null
var _adInGroup: String? = null
var _adHost: String? = null
var _adPass: String? = null
var _adPort: Int? = null
var _adUser: String? = null
var _adUserPath: String? = null
var _triggerDays: Long? = null
var _mailHost: String? = null
var _mailPort: Int? = null
var _mailUser: String? = null
var _mailPass: String? = null
var _mailingTime: String? = null
var _adminMailAddresses: List<String>? = null
var _enableStartTls: Boolean = false
var _debugMode: Boolean = false
var onlyAdminReportMode: Boolean = false


fun main(args: Array<String>) {
    var optind = 0
    while (optind < args.size) {
        if (args[optind] == "--ad_domain") _adDomain = args[++optind]
        else if (args[optind] == "--ad_ex_group") _adExGroup = args[++optind]
        else if (args[optind] == "--ad_in_group") _adInGroup = args[++optind]
        else if (args[optind] == "--ad_host") _adHost = args[++optind]
        else if (args[optind] == "--ad_pass") _adPass = args[++optind]
        else if (args[optind] == "--ad_port") _adPort = args[++optind].toInt()
        else if (args[optind] == "--ad_user") _adUser = args[++optind]
        else if (args[optind] == "--ad_user_path") _adUserPath = args[++optind]
        else if (args[optind] == "--trigger_days") _triggerDays = args[++optind].toLong()
        else if (args[optind] == "--mail_host") _mailHost = args[++optind]
        else if (args[optind] == "--mail_port") _mailPort = args[++optind].toInt()
        else if (args[optind] == "--mail_user") _mailUser = args[++optind]
        else if (args[optind] == "--mail_pass") _mailPass = args[++optind]
        else if (args[optind] == "--admin_mail_address") _adminMailAddresses = args[++optind].split(":")
        else if (args[optind] == "--only_admin_report_mode") onlyAdminReportMode = true
        else if (args[optind] == "--enable_start_tls") _enableStartTls = true
        else if (args[optind] == "--debug") _debugMode = true
        else if (args[optind] == "--mailing_time") {
            _mailingTime = args[++optind]
            if (!Regex("\\d\\d:\\d\\d").matches(_mailingTime!!)) {
                throw RuntimeException("Failed to parse '--mailing_time' variable.\nUsage: --mailing_time [MM]:[HH]")
            }
            _mailingTime = _mailingTime!!.replace(":", " ")
        }
        optind++
    }

    notifyAllUsersWhosePasswordsAboutToExpire()
    scheduleTask(executor)
    logger.info { "Password notifier ready to work!" }
}