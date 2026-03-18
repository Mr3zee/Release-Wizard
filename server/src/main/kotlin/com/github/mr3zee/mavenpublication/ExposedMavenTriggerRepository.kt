package com.github.mr3zee.mavenpublication

import com.github.mr3zee.model.MavenTrigger
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.persistence.MavenTriggerTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.UUID
import kotlin.time.Instant

class ExposedMavenTriggerRepository(private val db: Database) : MavenTriggerRepository {

    private val logger = LoggerFactory.getLogger(ExposedMavenTriggerRepository::class.java)

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction(db) { block() } }

    private fun ResultRow.toModel() = MavenTrigger(
        id = this[MavenTriggerTable.id].value.toString(),
        projectId = ProjectId(this[MavenTriggerTable.projectId].value.toString()),
        repoUrl = this[MavenTriggerTable.repoUrl],
        groupId = this[MavenTriggerTable.groupId],
        artifactId = this[MavenTriggerTable.artifactId],
        parameterKey = this[MavenTriggerTable.parameterKey],
        enabled = this[MavenTriggerTable.enabled],
        includeSnapshots = this[MavenTriggerTable.includeSnapshots],
        lastCheckedAt = this[MavenTriggerTable.lastCheckedAt],
    )

    private fun ResultRow.toWithVersions() = MavenTriggerWithVersions(
        trigger = toModel(),
        knownVersions = this[MavenTriggerTable.knownVersions],
    )

    override suspend fun findByProjectId(projectId: ProjectId): List<MavenTrigger> = dbQuery {
        MavenTriggerTable.selectAll()
            .where { MavenTriggerTable.projectId eq UUID.fromString(projectId.value) }
            .map { it.toModel() }
    }

    override suspend fun findById(id: String): MavenTrigger? = dbQuery {
        MavenTriggerTable.selectAll()
            .where { MavenTriggerTable.id eq UUID.fromString(id) }
            .singleOrNull()
            ?.toModel()
    }

    override suspend fun findAllEnabled(): List<MavenTriggerWithVersions> = dbQuery {
        MavenTriggerTable.selectAll()
            .where { MavenTriggerTable.enabled eq true }
            .map { it.toWithVersions() }
    }

    override suspend fun create(
        projectId: ProjectId,
        repoUrl: String,
        groupId: String,
        artifactId: String,
        parameterKey: String,
        includeSnapshots: Boolean,
        enabled: Boolean,
        knownVersions: Set<String>,
        createdBy: String,
    ): MavenTrigger = dbQuery {
        val id = UUID.randomUUID()
        MavenTriggerTable.insert {
            it[MavenTriggerTable.id] = id
            it[MavenTriggerTable.projectId] = UUID.fromString(projectId.value)
            it[MavenTriggerTable.repoUrl] = repoUrl
            it[MavenTriggerTable.groupId] = groupId
            it[MavenTriggerTable.artifactId] = artifactId
            it[MavenTriggerTable.parameterKey] = parameterKey
            it[MavenTriggerTable.includeSnapshots] = includeSnapshots
            it[MavenTriggerTable.enabled] = enabled
            it[MavenTriggerTable.knownVersions] = knownVersions
            it[MavenTriggerTable.createdBy] = UUID.fromString(createdBy)
            it[MavenTriggerTable.lastCheckedAt] = null
        }
        MavenTrigger(
            id = id.toString(),
            projectId = projectId,
            repoUrl = repoUrl,
            groupId = groupId,
            artifactId = artifactId,
            parameterKey = parameterKey,
            enabled = enabled,
            includeSnapshots = includeSnapshots,
            lastCheckedAt = null,
        )
    }

    override suspend fun updateEnabled(id: String, enabled: Boolean): MavenTrigger? = dbQuery {
        val uuid = UUID.fromString(id)
        val updated = MavenTriggerTable.update({ MavenTriggerTable.id eq uuid }) { stmt ->
            stmt[MavenTriggerTable.enabled] = enabled
        }
        if (updated > 0) {
            MavenTriggerTable.selectAll()
                .where { MavenTriggerTable.id eq uuid }
                .single()
                .toModel()
        } else {
            null
        }
    }

    override suspend fun updateKnownVersions(id: String, versions: Set<String>, checkedAt: Instant) = dbQuery {
        val updated = MavenTriggerTable.update({ MavenTriggerTable.id eq UUID.fromString(id) }) { stmt ->
            stmt[MavenTriggerTable.knownVersions] = versions
            stmt[MavenTriggerTable.lastCheckedAt] = checkedAt
        }
        if (updated == 0) {
            logger.warn("updateKnownVersions: no rows updated for trigger id={} — trigger may have been deleted", id)
        }
    }

    override suspend fun delete(id: String): Boolean = dbQuery {
        val deleted = MavenTriggerTable.deleteWhere {
            MavenTriggerTable.id eq UUID.fromString(id)
        }
        deleted > 0
    }
}
