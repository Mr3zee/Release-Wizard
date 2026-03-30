package com.github.mr3zee.usernotifications

import com.github.mr3zee.model.UserNotification
import com.github.mr3zee.model.UserNotificationType
import com.github.mr3zee.persistence.UserNotificationTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant

interface UserNotificationRepository {
    suspend fun insert(notification: UserNotification)
    suspend fun findByUser(userId: String, offset: Int, limit: Int): List<UserNotification>
    suspend fun countByUser(userId: String): Long
    suspend fun findByUserWithCount(userId: String, offset: Int, limit: Int): Pair<List<UserNotification>, Long>
    suspend fun countUnread(userId: String): Long
    suspend fun markAsRead(id: String, userId: String): Boolean
    suspend fun markAllAsRead(userId: String): Int
    suspend fun existsByUserAndTypeAndTarget(userId: String, type: UserNotificationType, targetType: String, targetId: String): Boolean
    suspend fun deleteOlderThan(cutoff: Instant): Int
    suspend fun deleteExcessPerUser(userId: String, maxCount: Int): Int
    suspend fun findUserIdsWithNotifications(): List<String>
}

class ExposedUserNotificationRepository(private val db: Database) : UserNotificationRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction(db) { block() } }

    private fun ResultRow.toUserNotification(): UserNotification {
        val typeStr = this[UserNotificationTable.type]
        val type = try {
            UserNotificationType.valueOf(typeStr)
        } catch (_: IllegalArgumentException) {
            UserNotificationType.UNKNOWN
        }
        val teamIdValue = this[UserNotificationTable.teamId]
        return UserNotification(
            id = this[UserNotificationTable.id].value.toString(),
            userId = this[UserNotificationTable.userId].value.toString(),
            type = type,
            teamId = teamIdValue?.let { com.github.mr3zee.model.TeamId(it) },
            teamName = this[UserNotificationTable.teamName],
            title = this[UserNotificationTable.title],
            message = this[UserNotificationTable.message],
            targetType = this[UserNotificationTable.targetType],
            targetId = this[UserNotificationTable.targetId],
            read = this[UserNotificationTable.read],
            timestamp = this[UserNotificationTable.timestamp].toEpochMilliseconds(),
        )
    }

    override suspend fun insert(notification: UserNotification) = dbQuery {
        val now = Clock.System.now()
        UserNotificationTable.insert {
            it[id] = UUID.randomUUID()
            it[userId] = UUID.fromString(notification.userId)
            it[type] = notification.type.name
            it[teamId] = notification.teamId?.value
            it[teamName] = notification.teamName
            it[title] = notification.title
            it[message] = notification.message
            it[targetType] = notification.targetType
            it[targetId] = notification.targetId
            it[read] = false
            it[timestamp] = if (notification.timestamp > 0) {
                Instant.fromEpochMilliseconds(notification.timestamp)
            } else {
                now
            }
        }
        Unit
    }

    override suspend fun findByUser(userId: String, offset: Int, limit: Int): List<UserNotification> = dbQuery {
        val userUuid = UUID.fromString(userId)
        UserNotificationTable.selectAll()
            .where { UserNotificationTable.userId eq userUuid }
            .orderBy(UserNotificationTable.timestamp, SortOrder.DESC)
            .limit(limit)
            .offset(offset.toLong())
            .map { it.toUserNotification() }
    }

    override suspend fun countByUser(userId: String): Long = dbQuery {
        val userUuid = UUID.fromString(userId)
        UserNotificationTable.selectAll()
            .where { UserNotificationTable.userId eq userUuid }
            .count()
    }

    override suspend fun findByUserWithCount(userId: String, offset: Int, limit: Int): Pair<List<UserNotification>, Long> {
        // Use a single transaction for consistent count + list
        return withContext(Dispatchers.IO) {
            suspendTransaction(
                transactionIsolation = java.sql.Connection.TRANSACTION_REPEATABLE_READ,
                db = db,
            ) {
                val userUuid = UUID.fromString(userId)
                val count = UserNotificationTable.selectAll()
                    .where { UserNotificationTable.userId eq userUuid }
                    .count()
                val list = UserNotificationTable.selectAll()
                    .where { UserNotificationTable.userId eq userUuid }
                    .orderBy(UserNotificationTable.timestamp, SortOrder.DESC)
                    .limit(limit)
                    .offset(offset.toLong())
                    .map { it.toUserNotification() }
                list to count
            }
        }
    }

    override suspend fun countUnread(userId: String): Long = dbQuery {
        val userUuid = UUID.fromString(userId)
        UserNotificationTable.selectAll()
            .where { (UserNotificationTable.userId eq userUuid) and (UserNotificationTable.read eq false) }
            .count()
    }

    override suspend fun markAsRead(id: String, userId: String): Boolean = dbQuery {
        val notifUuid = UUID.fromString(id)
        val userUuid = UUID.fromString(userId)
        val updated = UserNotificationTable.update({
            (UserNotificationTable.id eq notifUuid) and (UserNotificationTable.userId eq userUuid)
        }) {
            it[read] = true
        }
        updated > 0
    }

    override suspend fun markAllAsRead(userId: String): Int = dbQuery {
        val userUuid = UUID.fromString(userId)
        UserNotificationTable.update({
            (UserNotificationTable.userId eq userUuid) and (UserNotificationTable.read eq false)
        }) {
            it[read] = true
        }
    }

    override suspend fun existsByUserAndTypeAndTarget(
        userId: String,
        type: UserNotificationType,
        targetType: String,
        targetId: String,
    ): Boolean = dbQuery {
        val userUuid = UUID.fromString(userId)
        !UserNotificationTable.selectAll()
            .where {
                (UserNotificationTable.userId eq userUuid) and
                    (UserNotificationTable.type eq type.name) and
                    (UserNotificationTable.targetType eq targetType) and
                    (UserNotificationTable.targetId eq targetId)
            }
            .limit(1)
            .empty()
    }

    override suspend fun deleteOlderThan(cutoff: Instant): Int = dbQuery {
        UserNotificationTable.deleteWhere {
            UserNotificationTable.timestamp less cutoff
        }
    }

    override suspend fun deleteExcessPerUser(userId: String, maxCount: Int): Int = dbQuery {
        val userUuid = UUID.fromString(userId)
        // Find the IDs of notifications beyond the cap (sorted newest first, skip maxCount)
        val excessIds = UserNotificationTable
            .select(UserNotificationTable.id)
            .where { UserNotificationTable.userId eq userUuid }
            .orderBy(UserNotificationTable.timestamp, SortOrder.DESC)
            .offset(maxCount.toLong())
            .map { it[UserNotificationTable.id].value }

        if (excessIds.isNotEmpty()) {
            UserNotificationTable.deleteWhere {
                UserNotificationTable.id inList excessIds
            }
        } else {
            0
        }
    }

    override suspend fun findUserIdsWithNotifications(): List<String> = dbQuery {
        UserNotificationTable
            .select(UserNotificationTable.userId)
            .withDistinct()
            .map { it[UserNotificationTable.userId].value.toString() }
    }
}
