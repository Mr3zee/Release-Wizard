package com.github.mr3zee.notifications

import com.github.mr3zee.api.ReleaseEvent
import com.github.mr3zee.execution.ExecutionEngine
import com.github.mr3zee.model.NotificationConfig
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.model.ReleaseId
import com.github.mr3zee.model.ReleaseStatus
import com.github.mr3zee.releases.ReleasesRepository
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class NotificationListener(
    private val notificationRepository: NotificationRepository,
    private val releasesRepository: ReleasesRepository,
    private val httpClient: HttpClient,
) {
    private val log = LoggerFactory.getLogger(NotificationListener::class.java)

    fun start(engine: ExecutionEngine, scope: CoroutineScope) {
        scope.launch {
            engine.events.collect { event ->
                if (event is ReleaseEvent.ReleaseCompleted) {
                    try {
                        handleReleaseCompleted(event.releaseId, event.status)
                    } catch (e: Exception) {
                        log.error("Failed to send notification for release {}", event.releaseId.value, e)
                    }
                }
            }
        }
    }

    private suspend fun handleReleaseCompleted(releaseId: ReleaseId, status: ReleaseStatus) {
        val release = releasesRepository.findById(releaseId)
        if (release == null) {
            log.warn("Release {} not found when sending notifications", releaseId.value)
            return
        }
        sendNotifications(release.projectTemplateId, releaseId, status)
    }

    internal suspend fun sendNotifications(projectId: ProjectId, releaseId: ReleaseId, status: ReleaseStatus) {
        val configs = notificationRepository.findByProjectId(projectId)
        if (configs.isEmpty()) return

        log.info("Sending {} notification(s) for release {} (project {})", configs.size, releaseId.value, projectId.value)

        for (entity in configs) {
            if (!entity.enabled) continue
            try {
                when (entity.config) {
                    is NotificationConfig.SlackNotification -> sendSlackNotification(
                        entity.config,
                        releaseId,
                        status,
                    )
                }
            } catch (e: Exception) {
                log.error("Failed to send {} notification for release {}", entity.type, releaseId.value, e)
            }
        }
    }

    private suspend fun sendSlackNotification(
        config: NotificationConfig.SlackNotification,
        releaseId: ReleaseId,
        status: ReleaseStatus,
    ) {
        val emoji = when (status) {
            ReleaseStatus.SUCCEEDED -> ":white_check_mark:"
            ReleaseStatus.FAILED -> ":x:"
            ReleaseStatus.CANCELLED -> ":no_entry_sign:"
            else -> ":information_source:"
        }
        val text = "$emoji Release `${releaseId.value}` completed with status *${status.name}*"

        val payload = buildMap {
            put("text", text)
            if (config.channel.isNotBlank()) {
                put("channel", config.channel)
            }
        }

        httpClient.post(config.webhookUrl) {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }

        log.info("Sent Slack notification for release {} to channel {}", releaseId.value, config.channel)
    }
}
