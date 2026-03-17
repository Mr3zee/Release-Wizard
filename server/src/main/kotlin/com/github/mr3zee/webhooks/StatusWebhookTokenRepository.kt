package com.github.mr3zee.webhooks

import com.github.mr3zee.model.BlockId
import com.github.mr3zee.model.ReleaseId
import com.github.mr3zee.persistence.StatusWebhookTokenTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant

data class StatusWebhookToken(
    val token: UUID,
    val releaseId: ReleaseId,
    val blockId: BlockId,
    val active: Boolean,
    val createdAt: Instant,
)

interface StatusWebhookTokenRepository {
    suspend fun create(releaseId: ReleaseId, blockId: BlockId): UUID
    suspend fun findByToken(token: UUID): StatusWebhookToken?
    suspend fun deactivate(releaseId: ReleaseId, blockId: BlockId)
    suspend fun deactivateExpiredBefore(cutoff: Instant): Int
    suspend fun deleteInactiveBefore(cutoff: Instant): Int
}

class ExposedStatusWebhookTokenRepository(
    private val db: Database,
) : StatusWebhookTokenRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction(db) { block() } }

    override suspend fun create(releaseId: ReleaseId, blockId: BlockId): UUID = dbQuery {
        val token = UUID.randomUUID()
        val now = Clock.System.now()
        StatusWebhookTokenTable.insert {
            it[StatusWebhookTokenTable.id] = token
            it[StatusWebhookTokenTable.releaseId] = UUID.fromString(releaseId.value)
            it[StatusWebhookTokenTable.blockId] = blockId.value
            it[StatusWebhookTokenTable.active] = true
            it[StatusWebhookTokenTable.createdAt] = now
        }
        token
    }

    override suspend fun findByToken(token: UUID): StatusWebhookToken? = dbQuery {
        StatusWebhookTokenTable.selectAll()
            .where { StatusWebhookTokenTable.id eq token }
            .singleOrNull()
            ?.let {
                StatusWebhookToken(
                    token = it[StatusWebhookTokenTable.id].value,
                    releaseId = ReleaseId(it[StatusWebhookTokenTable.releaseId].value.toString()),
                    blockId = BlockId(it[StatusWebhookTokenTable.blockId]),
                    active = it[StatusWebhookTokenTable.active],
                    createdAt = it[StatusWebhookTokenTable.createdAt],
                )
            }
    }

    override suspend fun deactivate(releaseId: ReleaseId, blockId: BlockId) = dbQuery {
        StatusWebhookTokenTable.update({
            (StatusWebhookTokenTable.releaseId eq UUID.fromString(releaseId.value)) and
                (StatusWebhookTokenTable.blockId eq blockId.value) and
                (StatusWebhookTokenTable.active eq true)
        }) {
            it[StatusWebhookTokenTable.active] = false
        }
        Unit
    }

    override suspend fun deactivateExpiredBefore(cutoff: Instant): Int = dbQuery {
        StatusWebhookTokenTable.update({
            (StatusWebhookTokenTable.active eq true) and
                (StatusWebhookTokenTable.createdAt less cutoff)
        }) {
            it[StatusWebhookTokenTable.active] = false
        }
    }

    override suspend fun deleteInactiveBefore(cutoff: Instant): Int = dbQuery {
        StatusWebhookTokenTable.deleteWhere {
            (StatusWebhookTokenTable.active eq false) and
                (StatusWebhookTokenTable.createdAt less cutoff)
        }
    }
}
