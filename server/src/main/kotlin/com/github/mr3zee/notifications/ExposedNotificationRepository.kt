package com.github.mr3zee.notifications

import com.github.mr3zee.model.NotificationConfig
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.persistence.NotificationConfigTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.UUID

class ExposedNotificationRepository(private val db: Database) : NotificationRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction(db) { block() } }

    private fun ResultRow.toEntity(): NotificationConfigEntity {
        return NotificationConfigEntity(
            id = this[NotificationConfigTable.id].value.toString(),
            projectId = ProjectId(this[NotificationConfigTable.projectId]),
            userId = this[NotificationConfigTable.userId],
            type = this[NotificationConfigTable.type],
            config = this[NotificationConfigTable.config],
            enabled = this[NotificationConfigTable.enabled],
        )
    }

    override suspend fun findByProjectId(projectId: ProjectId): List<NotificationConfigEntity> = dbQuery {
        NotificationConfigTable.selectAll()
            .where { NotificationConfigTable.projectId eq projectId.value }
            .map { it.toEntity() }
    }

    override suspend fun findById(id: String): NotificationConfigEntity? = dbQuery {
        NotificationConfigTable.selectAll()
            .where { NotificationConfigTable.id eq UUID.fromString(id) }
            .singleOrNull()
            ?.toEntity()
    }

    override suspend fun create(
        projectId: ProjectId,
        userId: String,
        type: String,
        config: NotificationConfig,
        enabled: Boolean,
    ): NotificationConfigEntity = dbQuery {
        val id = UUID.randomUUID()
        NotificationConfigTable.insert {
            it[NotificationConfigTable.id] = id
            it[NotificationConfigTable.projectId] = projectId.value
            it[NotificationConfigTable.userId] = userId
            it[NotificationConfigTable.type] = type
            it[NotificationConfigTable.config] = config
            it[NotificationConfigTable.enabled] = enabled
        }
        NotificationConfigEntity(
            id = id.toString(),
            projectId = projectId,
            userId = userId,
            type = type,
            config = config,
            enabled = enabled,
        )
    }

    override suspend fun update(
        id: String,
        config: NotificationConfig?,
        enabled: Boolean?,
    ): NotificationConfigEntity? = dbQuery {
        val uuid = UUID.fromString(id)
        val updated = NotificationConfigTable.update({ NotificationConfigTable.id eq uuid }) { stmt ->
            config?.let { stmt[NotificationConfigTable.config] = it }
            enabled?.let { stmt[NotificationConfigTable.enabled] = it }
        }
        if (updated > 0) {
            NotificationConfigTable.selectAll()
                .where { NotificationConfigTable.id eq uuid }
                .single()
                .toEntity()
        } else {
            null
        }
    }

    override suspend fun delete(id: String): Boolean = dbQuery {
        val deleted = NotificationConfigTable.deleteWhere {
            NotificationConfigTable.id eq UUID.fromString(id)
        }
        deleted > 0
    }
}
