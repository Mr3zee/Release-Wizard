package com.github.mr3zee.auth

import com.github.mr3zee.model.UserId
import com.github.mr3zee.model.UserRole
import com.github.mr3zee.persistence.PatTable
import com.github.mr3zee.persistence.UserTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant

data class PatRecord(
    val id: UUID,
    val userId: UUID,
    val name: String,
    val expiresAt: Instant?,
    val lastUsedAt: Instant?,
    val createdAt: Instant,
    val revokedAt: Instant?,
)

data class PatWithUser(
    val pat: PatRecord,
    val username: String,
    val userRole: UserRole,
    val userApproved: Boolean,
    val passwordChangedAt: Instant?,
)

class PatRepository(private val db: Database) {
    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction(db) { block() } }

    /**
     * Atomically check the active token count and insert a new PAT in a single transaction.
     * Returns null if the user has reached [maxPerUser] active tokens.
     */
    suspend fun createIfUnderLimit(userId: UUID, name: String, tokenHash: String, expiresAt: Instant?, maxPerUser: Int): PatRecord? {
        val now = Clock.System.now()
        val id = UUID.randomUUID()
        return dbQuery {
            val activeCount = PatTable.selectAll()
                .where {
                    (PatTable.userId eq userId) and PatTable.revokedAt.isNull()
                }
                .count()
                .toInt()
            if (activeCount >= maxPerUser) return@dbQuery null
            PatTable.insert {
                it[PatTable.id] = id
                it[PatTable.userId] = userId
                it[PatTable.name] = name
                it[PatTable.tokenHash] = tokenHash
                it[PatTable.expiresAt] = expiresAt
                it[PatTable.createdAt] = now
            }
            PatRecord(
                id = id,
                userId = userId,
                name = name,
                expiresAt = expiresAt,
                lastUsedAt = null,
                createdAt = now,
                revokedAt = null,
            )
        }
    }

    /**
     * Look up a PAT by its SHA-256 hash, joined with the owning user's info.
     * Only returns active (non-revoked, non-expired) tokens. Approval check is
     * performed by the caller (PatService.validate).
     */
    suspend fun findActiveByTokenHash(tokenHash: String): PatWithUser? {
        val now = Clock.System.now()
        return dbQuery {
            (PatTable innerJoin UserTable)
                .selectAll()
                .where {
                    (PatTable.tokenHash eq tokenHash) and
                        PatTable.revokedAt.isNull() and
                        (PatTable.expiresAt.isNull() or (PatTable.expiresAt greater now))
                }
                .singleOrNull()
                ?.let { row ->
                    PatWithUser(
                        pat = PatRecord(
                            id = row[PatTable.id].value,
                            userId = row[PatTable.userId].value,
                            name = row[PatTable.name],
                            expiresAt = row[PatTable.expiresAt],
                            lastUsedAt = row[PatTable.lastUsedAt],
                            createdAt = row[PatTable.createdAt],
                            revokedAt = row[PatTable.revokedAt],
                        ),
                        username = row[UserTable.username],
                        userRole = row[UserTable.role],
                        userApproved = row[UserTable.approved],
                        passwordChangedAt = row[UserTable.passwordChangedAt],
                    )
                }
        }
    }

    suspend fun listByUser(userId: UserId): List<PatRecord> = dbQuery {
        PatTable.selectAll()
            .where { PatTable.userId eq UUID.fromString(userId.value) }
            .orderBy(PatTable.createdAt)
            .map { row ->
                PatRecord(
                    id = row[PatTable.id].value,
                    userId = row[PatTable.userId].value,
                    name = row[PatTable.name],
                    expiresAt = row[PatTable.expiresAt],
                    lastUsedAt = row[PatTable.lastUsedAt],
                    createdAt = row[PatTable.createdAt],
                    revokedAt = row[PatTable.revokedAt],
                )
            }
    }

    suspend fun countByUser(userId: UserId): Int = dbQuery {
        PatTable.selectAll()
            .where {
                (PatTable.userId eq UUID.fromString(userId.value)) and
                    PatTable.revokedAt.isNull()
            }
            .count()
            .toInt()
    }

    /**
     * Revoke a PAT. Returns true if the token was found and revoked, false if not found or already revoked.
     * When [ownerUserId] is null, any user's token can be revoked (admin operation).
     */
    suspend fun revoke(patId: UUID, ownerUserId: UserId?): Boolean {
        val now = Clock.System.now()
        return dbQuery {
            PatTable.update(
                where = {
                    (PatTable.id eq patId) and
                        PatTable.revokedAt.isNull() and
                        if (ownerUserId != null) {
                            PatTable.userId eq UUID.fromString(ownerUserId.value)
                        } else {
                            Op.TRUE
                        }
                }
            ) {
                it[PatTable.revokedAt] = now
            } > 0
        }
    }

    /**
     * Conditionally update lastUsedAt only if it is null or older than [threshold].
     * Uses a single atomic UPDATE to avoid read-then-write race conditions.
     */
    suspend fun updateLastUsedAt(patId: UUID, threshold: Instant) {
        val now = Clock.System.now()
        dbQuery {
            PatTable.update(
                where = {
                    (PatTable.id eq patId) and
                        (PatTable.lastUsedAt.isNull() or (PatTable.lastUsedAt less threshold))
                }
            ) {
                it[PatTable.lastUsedAt] = now
            }
        }
    }

    /**
     * Delete PATs that are revoked or expired and older than [olderThan].
     * Returns the number of deleted rows.
     */
    suspend fun deleteExpiredAndRevoked(olderThan: Instant): Int = dbQuery {
        PatTable.deleteWhere {
            (PatTable.revokedAt.isNotNull() and (PatTable.revokedAt less olderThan)) or
                (PatTable.expiresAt.isNotNull() and (PatTable.expiresAt less olderThan))
        }
    }
}
