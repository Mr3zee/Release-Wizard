package com.github.mr3zee.execution

import com.github.mr3zee.model.ReleaseStatus
import com.github.mr3zee.releases.ReleasesRepository
import com.github.mr3zee.webhooks.PendingWebhookRepository
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * Orchestrates release recovery on server startup.
 * Finds RUNNING releases in the database and resumes them via ExecutionEngine.
 */
class RecoveryService(
    private val releasesRepository: ReleasesRepository,
    private val webhookRepository: PendingWebhookRepository,
    private val executionEngine: ExecutionEngine,
) {
    private val log = LoggerFactory.getLogger(RecoveryService::class.java)

    suspend fun recover() {
        log.info("Starting release recovery...")

        // Clean up stale completed webhooks (older than 7 days)
        val cutoff = Clock.System.now() - 7.days
        val deletedWebhooks = webhookRepository.deleteCompletedOlderThan(cutoff)
        if (deletedWebhooks > 0) {
            log.info("Cleaned up {} stale completed webhooks", deletedWebhooks)
        }

        // Find all RUNNING and PENDING releases
        val runningReleases = releasesRepository.findByStatuses(
            setOf(ReleaseStatus.RUNNING, ReleaseStatus.PENDING),
        )

        if (runningReleases.isEmpty()) {
            log.info("No RUNNING/PENDING releases to recover")
            return
        }

        log.info("Found {} RUNNING/PENDING release(s) to recover", runningReleases.size)

        var resumed = 0
        var failed = 0
        for (release in runningReleases) {
            try {
                val executions = releasesRepository.findBlockExecutions(release.id)
                executionEngine.recoverRelease(release, executions)
                resumed++
                log.info("Recovery started for release {}", release.id.value)
            } catch (e: Exception) {
                failed++
                log.error("Failed to recover release {}: {}", release.id.value, e.message, e)
                // Mark the release as FAILED if recovery itself fails
                try {
                    releasesRepository.setFinished(release.id, ReleaseStatus.FAILED)
                } catch (e2: Exception) {
                    log.error("Failed to mark release {} as FAILED", release.id.value, e2)
                }
            }
        }

        log.info("Release recovery completed — {} resumed, {} failed", resumed, failed)
    }
}
