package com.github.mr3zee.api

import com.github.mr3zee.model.NotificationConfig
import com.github.mr3zee.model.ProjectId
import kotlinx.serialization.Serializable

@Serializable
data class CreateNotificationConfigRequest(
    val projectId: ProjectId,
    val type: String,
    val config: NotificationConfig,
    val enabled: Boolean = true,
)

@Serializable
data class NotificationConfigResponse(
    val id: String,
    val projectId: ProjectId,
    val type: String,
    val config: NotificationConfig,
    val enabled: Boolean,
)

@Serializable
data class NotificationConfigListResponse(
    val configs: List<NotificationConfigResponse>,
)
