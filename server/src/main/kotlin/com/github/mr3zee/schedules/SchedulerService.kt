package com.github.mr3zee.schedules

import com.github.mr3zee.releases.ReleasesService
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

class SchedulerService(
    private val scheduleRepository: ScheduleRepository,
    private val releasesService: ReleasesService,
) {
    private val logger = LoggerFactory.getLogger(SchedulerService::class.java)
    private var pollingJob: Job? = null

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
                delay(30_000L.milliseconds)
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
                    // SCHED-C1: Atomically claim the schedule before firing to prevent double-fire on crash recovery
                    val nextRun = CronUtils.computeNextRun(schedule.cronExpression)
                    val claimed = scheduleRepository.claimSchedule(schedule.id, now, nextRun)
                    if (!claimed) {
                        logger.debug("Schedule {} already claimed during recovery", schedule.id)
                        continue
                    }
                    logger.info("Catch-up firing schedule {} for project {}", schedule.id, schedule.projectId.value)
                    fireSchedule(schedule)
                } else {
                    // Just advance next_run_at to the future
                    advanceNextRun(schedule)
                }
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
                // SCHED-C1: Atomically claim before firing — CAS prevents double-fire across instances
                val nextRun = CronUtils.computeNextRun(schedule.cronExpression)
                val claimed = scheduleRepository.claimSchedule(schedule.id, now, nextRun)
                if (!claimed) {
                    logger.debug("Schedule {} already claimed by another instance", schedule.id)
                    continue
                }
                logger.info("Triggering scheduled release for project {}", schedule.projectId.value)
                fireSchedule(schedule)
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
        val nextRun = CronUtils.computeNextRun(schedule.cronExpression)
        scheduleRepository.update(
            id = schedule.id,
            enabled = null,
            nextRunAt = nextRun,
            lastRunAt = now,
        )
    }
}
