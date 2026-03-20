package com.github.mr3zee.audit

import com.github.mr3zee.model.*
import com.github.mr3zee.persistence.AuditEventTable
import com.github.mr3zee.persistence.safeOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.util.UUID
import kotlin.time.Clock

interface AuditRepository {
    suspend fun insert(event: AuditEvent)
    suspend fun findByTeam(teamId: TeamId, offset: Int = 0, limit: Int = 50): Pair<List<AuditEvent>, Long>
}

class ExposedAuditRepository(private val db: Database) : AuditRepository {
    private val log = LoggerFactory.getLogger(ExposedAuditRepository::class.java)

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction(db) { block() } }

    private suspend fun <T> dbQuery(transactionIsolation: Int, block: suspend () -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction(db, transactionIsolation = transactionIsolation) { block() } }

    private fun performInsert(event: AuditEvent) {
        AuditEventTable.insert {
            it[id] = UUID.randomUUID()
            it[teamId] = event.teamId?.value
            it[actorUserId] = event.actorUserId?.let { uid -> UUID.fromString(uid.value) }
            it[actorUsername] = event.actorUsername
            it[action] = event.action.name
            it[targetType] = event.targetType.name
            it[targetId] = event.targetId
            it[details] = event.details
            it[timestamp] = Clock.System.now()
        }
    }

    override suspend fun insert(event: AuditEvent): Unit = dbQuery {
        performInsert(event)
    }

    // TAG-M2: REPEATABLE_READ ensures count + data queries see consistent snapshot
    override suspend fun findByTeam(teamId: TeamId, offset: Int, limit: Int): Pair<List<AuditEvent>, Long> = dbQuery(Connection.TRANSACTION_REPEATABLE_READ) {
        val totalCount = AuditEventTable.selectAll()
            .where { AuditEventTable.teamId eq teamId.value }
            .count()

        val events = AuditEventTable.selectAll()
            .where { AuditEventTable.teamId eq teamId.value }
            .orderBy(AuditEventTable.timestamp, SortOrder.DESC)
            .limit(limit)
            .offset(safeOffset(offset))
            .map { it.toAuditEvent() }

        events to totalCount
    }

    private fun ResultRow.toAuditEvent(): AuditEvent {
        // TAG-M6: Gracefully handle unknown enum values instead of crashing with IllegalArgumentException
        val actionStr = this[AuditEventTable.action]
        val action = runCatching { AuditAction.valueOf(actionStr) }.getOrElse {
            log.warn("Unknown AuditAction '{}' found in audit event, mapping to UNKNOWN", actionStr)
            AuditAction.UNKNOWN
        }
        val targetTypeStr = this[AuditEventTable.targetType]
        val targetType = runCatching { AuditTargetType.valueOf(targetTypeStr) }.getOrElse {
            log.warn("Unknown AuditTargetType '{}' found in audit event, mapping to UNKNOWN", targetTypeStr)
            AuditTargetType.UNKNOWN
        }
        return AuditEvent(
            id = this[AuditEventTable.id].value.toString(),
            teamId = this[AuditEventTable.teamId]?.let { TeamId(it) },
            actorUserId = this[AuditEventTable.actorUserId]?.let { entityId -> UserId(entityId.value.toString()) },
            actorUsername = this[AuditEventTable.actorUsername],
            action = action,
            targetType = targetType,
            targetId = this[AuditEventTable.targetId],
            details = this[AuditEventTable.details],
            timestamp = this[AuditEventTable.timestamp].toEpochMilliseconds(),
        )
    }
}
