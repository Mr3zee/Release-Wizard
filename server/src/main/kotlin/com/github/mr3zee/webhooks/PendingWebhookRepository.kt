package com.github.mr3zee.webhooks

import com.github.mr3zee.model.BlockId
import com.github.mr3zee.model.ConnectionId
import com.github.mr3zee.model.ReleaseId
import com.github.mr3zee.persistence.PendingWebhookTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.UUID

interface PendingWebhookRepository {
    suspend fun create(
        externalId: String,
        blockId: BlockId,
        releaseId: ReleaseId,
        connectionId: ConnectionId,
        type: WebhookType,
    ): PendingWebhook

    suspend fun findByExternalIdAndType(externalId: String, type: WebhookType): PendingWebhook?
    suspend fun findPendingByExternalIdAndType(externalId: String, type: WebhookType): PendingWebhook?
    suspend fun findByConnectionIdAndType(connectionId: ConnectionId, type: WebhookType): List<PendingWebhook>
    suspend fun findPendingByReleaseId(releaseId: ReleaseId): List<PendingWebhook>
    suspend fun findByReleaseIdAndBlockId(releaseId: ReleaseId, blockId: BlockId): PendingWebhook?
    suspend fun updateStatus(id: String, status: WebhookStatus, payload: String? = null): Boolean
    suspend fun updateExternalId(id: String, externalId: String): Boolean
    suspend fun deleteCompletedOlderThan(cutoff: Instant): Int
}

class ExposedPendingWebhookRepository(
    private val db: Database,
) : PendingWebhookRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction(db) { block() } }

    private fun ResultRow.toWebhook(): PendingWebhook = PendingWebhook(
        id = this[PendingWebhookTable.id].value.toString(),
        externalId = this[PendingWebhookTable.externalId],
        blockId = BlockId(this[PendingWebhookTable.blockId]),
        releaseId = ReleaseId(this[PendingWebhookTable.releaseId]),
        connectionId = ConnectionId(this[PendingWebhookTable.connectionId]),
        type = WebhookType.valueOf(this[PendingWebhookTable.type]),
        status = WebhookStatus.valueOf(this[PendingWebhookTable.status]),
        payload = this[PendingWebhookTable.payload],
        createdAt = this[PendingWebhookTable.createdAt],
        updatedAt = this[PendingWebhookTable.updatedAt],
    )

    override suspend fun create(
        externalId: String,
        blockId: BlockId,
        releaseId: ReleaseId,
        connectionId: ConnectionId,
        type: WebhookType,
    ): PendingWebhook = dbQuery {
        val now = Clock.System.now()
        val id = UUID.randomUUID()
        PendingWebhookTable.insert {
            it[PendingWebhookTable.id] = id
            it[PendingWebhookTable.externalId] = externalId
            it[PendingWebhookTable.blockId] = blockId.value
            it[PendingWebhookTable.releaseId] = releaseId.value
            it[PendingWebhookTable.connectionId] = connectionId.value
            it[PendingWebhookTable.type] = type.name
            it[PendingWebhookTable.status] = WebhookStatus.PENDING.name
            it[PendingWebhookTable.payload] = null
            it[createdAt] = now
            it[updatedAt] = now
        }
        PendingWebhook(
            id = id.toString(),
            externalId = externalId,
            blockId = blockId,
            releaseId = releaseId,
            connectionId = connectionId,
            type = type,
            status = WebhookStatus.PENDING,
            createdAt = now,
            updatedAt = now,
        )
    }

    override suspend fun findByExternalIdAndType(externalId: String, type: WebhookType): PendingWebhook? = dbQuery {
        PendingWebhookTable.selectAll()
            .where {
                (PendingWebhookTable.externalId eq externalId) and
                    (PendingWebhookTable.type eq type.name)
            }
            .orderBy(PendingWebhookTable.createdAt, SortOrder.DESC)
            .firstOrNull()
            ?.toWebhook()
    }

    override suspend fun findPendingByExternalIdAndType(externalId: String, type: WebhookType): PendingWebhook? = dbQuery {
        PendingWebhookTable.selectAll()
            .where {
                (PendingWebhookTable.externalId eq externalId) and
                    (PendingWebhookTable.type eq type.name) and
                    (PendingWebhookTable.status eq WebhookStatus.PENDING.name)
            }
            .orderBy(PendingWebhookTable.createdAt, SortOrder.DESC)
            .firstOrNull()
            ?.toWebhook()
    }

    override suspend fun findByConnectionIdAndType(
        connectionId: ConnectionId,
        type: WebhookType,
    ): List<PendingWebhook> = dbQuery {
        PendingWebhookTable.selectAll()
            .where {
                (PendingWebhookTable.connectionId eq connectionId.value) and
                    (PendingWebhookTable.type eq type.name) and
                    (PendingWebhookTable.status eq WebhookStatus.PENDING.name)
            }
            .map { it.toWebhook() }
    }

    override suspend fun findPendingByReleaseId(releaseId: ReleaseId): List<PendingWebhook> = dbQuery {
        PendingWebhookTable.selectAll()
            .where {
                (PendingWebhookTable.releaseId eq releaseId.value) and
                    (PendingWebhookTable.status eq WebhookStatus.PENDING.name)
            }
            .map { it.toWebhook() }
    }

    override suspend fun findByReleaseIdAndBlockId(releaseId: ReleaseId, blockId: BlockId): PendingWebhook? = dbQuery {
        PendingWebhookTable.selectAll()
            .where {
                (PendingWebhookTable.releaseId eq releaseId.value) and
                    (PendingWebhookTable.blockId eq blockId.value)
            }
            .orderBy(PendingWebhookTable.createdAt, SortOrder.DESC)
            .firstOrNull()
            ?.toWebhook()
    }

    override suspend fun updateStatus(id: String, status: WebhookStatus, payload: String?): Boolean = dbQuery {
        val now = Clock.System.now()
        val updated = PendingWebhookTable.update({ PendingWebhookTable.id eq UUID.fromString(id) }) {
            it[PendingWebhookTable.status] = status.name
            it[PendingWebhookTable.updatedAt] = now
            if (payload != null) {
                it[PendingWebhookTable.payload] = payload
            }
        }
        updated > 0
    }

    override suspend fun updateExternalId(id: String, externalId: String): Boolean = dbQuery {
        val now = Clock.System.now()
        val updated = PendingWebhookTable.update({ PendingWebhookTable.id eq UUID.fromString(id) }) {
            it[PendingWebhookTable.externalId] = externalId
            it[PendingWebhookTable.updatedAt] = now
        }
        updated > 0
    }

    override suspend fun deleteCompletedOlderThan(cutoff: Instant): Int = dbQuery {
        PendingWebhookTable.deleteWhere {
            (PendingWebhookTable.status eq WebhookStatus.COMPLETED.name) and
                (PendingWebhookTable.updatedAt less cutoff)
        }
    }
}
