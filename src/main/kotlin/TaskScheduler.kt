package snkt.org

import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

private val cronExpression = _mailingTime
    ?: throw IllegalStateException("'--mailing_time' variable is not set. Usage: '--mailing_time [MM]:[HH]'")

private val cronParser = CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX))
private val cron = cronParser.parse("$cronExpression * * *")
private val executionTime = ExecutionTime.forCron(cron)

fun scheduleTask(executor: ScheduledExecutorService) {
    val now = ZonedDateTime.now()
    val next = executionTime.nextExecution(now).get()
    val delay = Duration.between(now, next).seconds

    executor.schedule({
        try {
            notifyAllUsersWhosePasswordsAboutToExpire()
        } catch (e: Exception) {
            logger.error(e) { "Task failed to execute." }
        }
        scheduleTask(executor)
    }, delay, TimeUnit.SECONDS)
    logger.debug { "Scheduled task $next" }
}

fun scheduleTask(executor: ScheduledExecutorService, secondsDelay: Long) {
    executor.schedule({
        try {
            notifyAllUsersWhosePasswordsAboutToExpire()
        } catch (e: Exception) {
            logger.error(e) { "Task failed to execute." }
        }
        scheduleTask(executor, secondsDelay)
    }, secondsDelay, TimeUnit.SECONDS)
}