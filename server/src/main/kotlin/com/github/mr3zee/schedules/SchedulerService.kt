package com.github.mr3zee.schedules

import com.github.mr3zee.releases.ReleasesService
import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.time.Clock
import kotlin.time.Instant

class SchedulerService(
    private val scheduleRepository: ScheduleRepository,
    private val releasesService: ReleasesService,
    private val scheduleService: ScheduleService,
) {
    private val logger = LoggerFactory.getLogger(SchedulerService::class.java)
    private var pollingJob: Job? = null

    private val cronParser = CronParser(
        CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX)
    )

    /**
     * Start the scheduler polling loop.
     * First recovers missed schedules, then polls every 30 seconds.
     */
    fun start(scope: CoroutineScope) {
        pollingJob = scope.launch {
            recoverMissedSchedules()
            while (isActive) {
                try {
                    pollDueSchedules()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error("Scheduler poll error", e)
                }
                delay(30_000L)
            }
        }
    }

    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
    }

    /**
     * On startup, check for missed schedules where next_run_at is in the past.
     * If last_run_at doesn't cover that window, fire once (catch-up), then advance next_run_at.
     */
    private suspend fun recoverMissedSchedules() {
        val now = Clock.System.now()
        val dueSchedules = scheduleRepository.findDueSchedules(now)

        for (schedule in dueSchedules) {
            try {
                val shouldCatchUp = schedule.lastRunAt == null ||
                    (schedule.nextRunAt != null && schedule.lastRunAt < schedule.nextRunAt)

                if (shouldCatchUp) {
                    logger.info("Catch-up firing schedule {} for project {}", schedule.id, schedule.projectId.value)
                    fireSchedule(schedule)
                }

                // Advance next_run_at to the future
                advanceNextRun(schedule)
            } catch (e: Exception) {
                logger.error("Failed to recover schedule {}: {}", schedule.id, e.message, e)
            }
        }
    }

    private suspend fun pollDueSchedules() {
        val now = Clock.System.now()
        val dueSchedules = scheduleRepository.findDueSchedules(now)

        for (schedule in dueSchedules) {
            try {
                logger.info("Triggering scheduled release for project {}", schedule.projectId.value)
                fireSchedule(schedule)
                advanceNextRun(schedule)
            } catch (e: Exception) {
                logger.error("Failed to trigger schedule {}: {}", schedule.id, e.message, e)
            }
        }
    }

    private suspend fun fireSchedule(schedule: ScheduleEntity) {
        releasesService.startScheduledRelease(schedule.projectId, schedule.parameters)
    }

    private suspend fun advanceNextRun(schedule: ScheduleEntity) {
        val now = Clock.System.now()
        val nextRun = computeNextRun(schedule.cronExpression)
        scheduleRepository.update(
            id = schedule.id,
            enabled = null,
            nextRunAt = nextRun,
            lastRunAt = now,
        )
    }

    private fun computeNextRun(cronExpression: String): Instant? {
        return try {
            val cron = cronParser.parse(cronExpression)
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
