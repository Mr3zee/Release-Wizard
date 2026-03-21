package com.github.mr3zee.mavenpublication

import com.github.mr3zee.model.Parameter
import com.github.mr3zee.releases.ReleasesService
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class MavenPollerService(
    private val repository: MavenTriggerRepository,
    private val fetcher: MavenMetadataFetcher,
    private val releasesService: ReleasesService,
) {
    private val logger = LoggerFactory.getLogger(MavenPollerService::class.java)

    @Volatile
    private var pollingJob: Job? = null

    fun start(scope: CoroutineScope) {
        pollingJob = scope.launch {
            while (isActive) {
                // MAVEN-C1: Fixed-rate ticker — measure from cycle start, not end
                val cycleStart = Clock.System.now()
                try {
                    // MAVEN-M3: Bound total poll cycle duration to prevent runaway cycles
                    val completed = withTimeoutOrNull(POLL_TIMEOUT) {
                        pollAllTriggers()
                    }
                    if (completed == null) {
                        logger.warn("Maven poll cycle timed out after {}", POLL_TIMEOUT)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error("Maven poller error", e)
                }
                val elapsed = Clock.System.now() - cycleStart
                val remaining = POLL_INTERVAL - elapsed
                if (remaining.isPositive()) {
                    delay(remaining)
                }
            }
        }
    }

    // MAVEN-L2: Cancel and signal for clean shutdown
    fun stop() {
        val job = pollingJob ?: return
        pollingJob = null
        job.cancel()
    }

    internal suspend fun pollAllTriggers() {
        val allEnabled = repository.findAllEnabled()
        // Process in batches of 10 to cap concurrent outbound HTTP requests
        allEnabled.chunked(CONCURRENT_FETCH_LIMIT).forEach { batch ->
            coroutineScope {
                batch.forEach { entry ->
                    launch { pollSingleTrigger(entry) }
                }
            }
        }
    }

    private suspend fun pollSingleTrigger(entry: MavenTriggerWithVersions) {
        val trigger = entry.trigger
        val versions = fetcher.fetch(trigger.repoUrl, trigger.groupId, trigger.artifactId)
            ?: return // network/parse error; skip this cycle, lastCheckedAt unchanged

        val allNew = versions - entry.knownVersions
        val newVersions = if (trigger.includeSnapshots) {
            allNew
        } else {
            allNew.filter { !it.endsWith("-SNAPSHOT", ignoreCase = true) }.toSet()
        }

        if (newVersions.isEmpty()) {
            // No new versions — always update lastCheckedAt to record a successful poll,
            // and sync knownVersions with current repo state (handles version removals).
            repository.updateKnownVersions(trigger.id, versions, Clock.System.now())
            return
        }

        val fired = mutableSetOf<String>()
        newVersions.forEach { version ->
            try {
                releasesService.startScheduledRelease(
                    trigger.projectId,
                    listOf(Parameter(trigger.parameterKey, version)),
                )
                fired += version
                logger.info(
                    "Fired release for {}:{} version {} (parameter key: {})",
                    trigger.groupId, trigger.artifactId, version, trigger.parameterKey,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(
                    "Failed to fire release for {}:{} version {}: {}",
                    trigger.groupId, trigger.artifactId, version, e.message, e,
                )
                // Don't add to fired — version will retry on next poll
            }
        }

        if (fired.isNotEmpty()) {
            repository.updateKnownVersions(
                trigger.id,
                entry.knownVersions + fired,
                Clock.System.now(),
            )
        }
    }

    companion object {
        private val POLL_INTERVAL = 5.minutes
        /** MAVEN-M3: Maximum duration for a single poll cycle */
        private val POLL_TIMEOUT = 4.minutes
        private const val CONCURRENT_FETCH_LIMIT = 10
    }
}
