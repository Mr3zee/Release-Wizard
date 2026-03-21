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
import kotlin.time.Duration
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
    /** HOOK-M5: Atomically find token, validate active + TTL, deactivate if expired — single transaction */
    suspend fun findActiveToken(token: UUID, ttl: Duration): StatusWebhookToken?
    /**
     * HOOK-H1: Atomically find active token within TTL AND deactivate it in the same transaction.
     * Prevents replay attacks — the token can only be used once.
     * Returns the token record (with active=false) if it was valid, null if not found/expired/already used.
     */
    suspend fun findAndDeactivateToken(token: UUID, ttl: Duration): StatusWebhookToken?
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
        // HOOK-H3: Deactivate existing active tokens for same (releaseId, blockId) before creating new one
        StatusWebhookTokenTable.update({
            (StatusWebhookTokenTable.releaseId eq UUID.fromString(releaseId.value)) and
                (StatusWebhookTokenTable.blockId eq blockId.value) and
                (StatusWebhookTokenTable.active eq true)
        }) {
            it[StatusWebhookTokenTable.active] = false
        }
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
            ?.toStatusWebhookToken()
    }

    /**
     * HOOK-M5: Atomically find, validate, and deactivate-if-expired in a single transaction.
     * Returns the token record if active and within TTL, null otherwise.
     */
    override suspend fun findActiveToken(token: UUID, ttl: Duration): StatusWebhookToken? = dbQuery {
        fetchAndValidateToken(token, ttl)?.toStatusWebhookToken()
    }

    /**
     * HOOK-H1: Atomically find + validate + deactivate in a single transaction.
     * Uses FOR UPDATE to prevent concurrent use of the same token.
     */
    override suspend fun findAndDeactivateToken(token: UUID, ttl: Duration): StatusWebhookToken? = dbQuery {
        val row = fetchAndValidateToken(token, ttl) ?: return@dbQuery null

        // Deactivate the token in the same transaction — single-use guarantee
        StatusWebhookTokenTable.update({
            (StatusWebhookTokenTable.id eq token) and (StatusWebhookTokenTable.active eq true)
        }) {
            it[StatusWebhookTokenTable.active] = false
        }

        row.toStatusWebhookToken(active = false)
    }

    private fun ResultRow.toStatusWebhookToken(active: Boolean = this[StatusWebhookTokenTable.active]) =
        StatusWebhookToken(
            token = this[StatusWebhookTokenTable.id].value,
            releaseId = ReleaseId(this[StatusWebhookTokenTable.releaseId].value.toString()),
            blockId = BlockId(this[StatusWebhookTokenTable.blockId]),
            active = active,
            createdAt = this[StatusWebhookTokenTable.createdAt],
        )

    /**
     * Locks the token row, validates it's active and within TTL.
     * Deactivates expired tokens atomically. Returns the valid row or null.
     */
    private fun fetchAndValidateToken(token: UUID, ttl: Duration): ResultRow? {
        val row = StatusWebhookTokenTable.selectAll()
            .where { StatusWebhookTokenTable.id eq token }
            .forUpdate()
            .singleOrNull() ?: return null

        if (!row[StatusWebhookTokenTable.active]) return null

        val createdAt = row[StatusWebhookTokenTable.createdAt]
        val age = Clock.System.now() - createdAt
        if (age > ttl) {
            StatusWebhookTokenTable.update({
                (StatusWebhookTokenTable.id eq token) and (StatusWebhookTokenTable.active eq true)
            }) {
                it[StatusWebhookTokenTable.active] = false
            }
            return null
        }

        return row
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
