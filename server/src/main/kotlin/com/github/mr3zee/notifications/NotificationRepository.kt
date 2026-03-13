package com.github.mr3zee.notifications

import com.github.mr3zee.model.NotificationConfig
import com.github.mr3zee.model.ProjectId

data class NotificationConfigEntity(
    val id: String,
    val projectId: ProjectId,
    val userId: String,
    val type: String,
    val config: NotificationConfig,
    val enabled: Boolean,
)

interface NotificationRepository {
    suspend fun findByProjectId(projectId: ProjectId): List<NotificationConfigEntity>
    suspend fun findById(id: String): NotificationConfigEntity?
    suspend fun create(
        projectId: ProjectId,
        userId: String,
        type: String,
        config: NotificationConfig,
        enabled: Boolean,
    ): NotificationConfigEntity
    suspend fun update(id: String, config: NotificationConfig?, enabled: Boolean?): NotificationConfigEntity?
    suspend fun delete(id: String): Boolean
}
