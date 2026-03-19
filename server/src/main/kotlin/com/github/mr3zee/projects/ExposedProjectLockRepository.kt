package com.github.mr3zee.projects

import com.github.mr3zee.api.ProjectLockInfo
import com.github.mr3zee.persistence.ProjectLockTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class ExposedProjectLockRepository(
    private val db: Database,
    private val clock: Clock = Clock.System,
) : ProjectLockRepository {
    private val log = LoggerFactory.getLogger(ExposedProjectLockRepository::class.java)

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction(db) { block() } }

    override suspend fun tryAcquire(
        projectId: String,
        userId: String,
        username: String,
        ttlMinutes: Long,
    ): ProjectLockInfo? = dbQuery {
        val now = clock.now()
        val expires = now + ttlMinutes.minutes
        val projectUuid = UUID.fromString(projectId)
        val userUuid = UUID.fromString(userId)

        // Check existing lock
        val existing = ProjectLockTable.selectAll()
            .where { ProjectLockTable.projectId eq projectUuid }
            .forUpdate()
            .singleOrNull()

        if (existing != null) {
            val existingUserId = existing[ProjectLockTable.userId].value
            val existingExpires = existing[ProjectLockTable.expiresAt]

            if (existingUserId == userUuid) {
                // Same user — extend TTL only (preserve original acquiredAt)
                ProjectLockTable.update(
                    where = { ProjectLockTable.projectId eq projectUuid }
                ) {
                    it[ProjectLockTable.expiresAt] = expires
                }
                return@dbQuery ProjectLockInfo(
                    userId, username,
                    existing[ProjectLockTable.acquiredAt], expires,
                )
            }

            if (existingExpires > now) {
                // Active lock held by another user
                return@dbQuery null
            }

            // PROJ-C2: Expired lock — atomically update in-place instead of delete-then-insert
            // The row is already locked via forUpdate(), so this is safe from races
            ProjectLockTable.update(
                where = { ProjectLockTable.projectId eq projectUuid }
            ) {
                it[ProjectLockTable.userId] = userUuid
                it[ProjectLockTable.username] = username
                it[ProjectLockTable.acquiredAt] = now
                it[ProjectLockTable.expiresAt] = expires
            }
            return@dbQuery ProjectLockInfo(userId, username, now, expires)
        }

        // No existing lock — insert new one. PK constraint prevents concurrent double-insert.
        try {
            ProjectLockTable.insert {
                it[ProjectLockTable.projectId] = projectUuid
                it[ProjectLockTable.userId] = userUuid
                it[ProjectLockTable.username] = username
                it[ProjectLockTable.acquiredAt] = now
                it[ProjectLockTable.expiresAt] = expires
            }
            ProjectLockInfo(userId, username, now, expires)
        } catch (e: org.jetbrains.exposed.v1.exceptions.ExposedSQLException) {
            val sqlState = (e.cause as? java.sql.SQLException)?.sqlState
            if (sqlState != null && sqlState.startsWith("23")) {
                log.debug("Lock acquisition race lost for project {} by user {}", projectId, userId)
                null
            } else {
                throw e
            }
        }
    }

    override suspend fun release(projectId: String, userId: String): Boolean = dbQuery {
        val projectUuid = UUID.fromString(projectId)
        val userUuid = UUID.fromString(userId)
        val deleted = ProjectLockTable.deleteWhere {
            (ProjectLockTable.projectId eq projectUuid) and (ProjectLockTable.userId eq userUuid)
        }
        deleted > 0
    }

    override suspend fun forceRelease(projectId: String): Boolean = dbQuery {
        val projectUuid = UUID.fromString(projectId)
        val deleted = ProjectLockTable.deleteWhere {
            ProjectLockTable.projectId eq projectUuid
        }
        deleted > 0
    }

    override suspend fun heartbeat(
        projectId: String,
        userId: String,
        ttlMinutes: Long,
    ): ProjectLockInfo? = dbQuery {
        val now = clock.now()
        val expires = now + ttlMinutes.minutes
        val projectUuid = UUID.fromString(projectId)
        val userUuid = UUID.fromString(userId)

        val updated = ProjectLockTable.update(
            where = {
                (ProjectLockTable.projectId eq projectUuid) and
                    (ProjectLockTable.userId eq userUuid) and
                    (ProjectLockTable.expiresAt greater now)
            }
        ) {
            it[ProjectLockTable.expiresAt] = expires
        }

        if (updated > 0) {
            // Read back to get the stored acquiredAt
            ProjectLockTable.selectAll()
                .where { ProjectLockTable.projectId eq projectUuid }
                .singleOrNull()
                ?.let { row ->
                    ProjectLockInfo(
                        userId = row[ProjectLockTable.userId].value.toString(),
                        username = row[ProjectLockTable.username],
                        acquiredAt = row[ProjectLockTable.acquiredAt],
                        expiresAt = row[ProjectLockTable.expiresAt],
                    )
                }
        } else {
            null // Lock lost
        }
    }

    override suspend fun findActiveLock(projectId: String): ProjectLockInfo? = dbQuery {
        val now = clock.now()
        val projectUuid = UUID.fromString(projectId)
        ProjectLockTable.selectAll()
            .where {
                (ProjectLockTable.projectId eq projectUuid) and
                    (ProjectLockTable.expiresAt greater now)
            }
            .singleOrNull()
            ?.let { row ->
                ProjectLockInfo(
                    userId = row[ProjectLockTable.userId].value.toString(),
                    username = row[ProjectLockTable.username],
                    acquiredAt = row[ProjectLockTable.acquiredAt],
                    expiresAt = row[ProjectLockTable.expiresAt],
                )
            }
    }
}
