package com.github.mr3zee.schedules

import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.time.Instant

object CronUtils {
    val parser: CronParser = CronParser(
        CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX)
    )

    private const val MIN_INTERVAL_MINUTES = 5L

    fun computeNextRun(cronExpression: String): Instant? {
        return try {
            val cron = parser.parse(cronExpression)
            val executionTime = ExecutionTime.forCron(cron)
            val next = executionTime.nextExecution(ZonedDateTime.now(ZoneOffset.UTC))
            if (next.isPresent) {
                Instant.fromEpochMilliseconds(next.get().toInstant().toEpochMilli())
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Validates that a cron expression does not fire more frequently than once per [MIN_INTERVAL_MINUTES] minutes.
     * Throws [IllegalArgumentException] if the interval is too short.
     */
    fun validateMinimumInterval(cronExpression: String) {
        try {
            val cron = parser.parse(cronExpression)
            val executionTime = ExecutionTime.forCron(cron)
            val now = ZonedDateTime.now(ZoneOffset.UTC)
            val firstExec = executionTime.nextExecution(now)
            if (firstExec.isPresent) {
                val secondExec = executionTime.nextExecution(firstExec.get())
                if (secondExec.isPresent) {
                    val intervalMinutes = java.time.Duration.between(
                        firstExec.get().toInstant(),
                        secondExec.get().toInstant(),
                    ).toMinutes()
                    if (intervalMinutes < MIN_INTERVAL_MINUTES) {
                        throw IllegalArgumentException(
                            "Schedule interval must be at least $MIN_INTERVAL_MINUTES minutes, but expression fires every $intervalMinutes minute(s)"
                        )
                    }
                }
            }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            // SCHED-H2: Log warning when interval cannot be computed (library bug or unusual expression).
            // The cron expression has already been parsed and validated — rejecting here could block
            // valid expressions that trigger edge cases in the cron-utils interval computation.
            val log = org.slf4j.LoggerFactory.getLogger("com.github.mr3zee.schedules.CronUtils")
            log.warn("Could not compute interval for cron expression '{}': {}", cronExpression, e.message)
        }
    }
}
