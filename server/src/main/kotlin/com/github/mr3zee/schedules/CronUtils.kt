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
}
