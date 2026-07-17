package snkt.org

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.util.concurrent.Executors

val logger = KotlinLogging.logger {}
val executor = Executors.newSingleThreadScheduledExecutor()

fun main() {
    scheduleTask(executor)
    logger.info { "Password notifier ready to work!" }
//    sendMail(initMailSession(), "pupkin.v@snkt.dev", "Hello Email!", File("index.html").readText())
//    notifyAllUsersWhosePasswordsAboutToExpire()
}