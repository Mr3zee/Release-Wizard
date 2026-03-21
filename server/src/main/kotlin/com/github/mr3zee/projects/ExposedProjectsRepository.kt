package com.github.mr3zee.projects

import com.github.mr3zee.api.ProjectLockInfo
import com.github.mr3zee.model.*
import com.github.mr3zee.persistence.ProjectLockTable
import com.github.mr3zee.persistence.ProjectTemplateTable
import com.github.mr3zee.persistence.likeContains
import com.github.mr3zee.persistence.safeOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.sql.Connection
import java.util.UUID
import kotlin.time.Clock

class ExposedProjectsRepository(private val db: Database) : ProjectsRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction(db) { block() } }

    private suspend fun <T> dbQuery(transactionIsolation: Int, block: suspend () -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction(db, transactionIsolation = transactionIsolation) { block() } }

    private fun ResultRow.toProjectTemplate(): ProjectTemplate {
        return ProjectTemplate(
            id = ProjectId(this[ProjectTemplateTable.id].value.toString()),
            name = this[ProjectTemplateTable.name],
            description = this[ProjectTemplateTable.description],
            dagGraph = this[ProjectTemplateTable.dagGraph],
            parameters = this[ProjectTemplateTable.parameters],
            defaultTags = this[ProjectTemplateTable.defaultTags],
            createdAt = this[ProjectTemplateTable.createdAt],
            updatedAt = this[ProjectTemplateTable.updatedAt],
        )
    }

    private fun buildProjectConditions(
        teamId: String?,
        teamIds: List<String>?,
        search: String?,
    ): List<Op<Boolean>> {
        val conditions = mutableListOf<Op<Boolean>>()
        if (teamId != null) {
            conditions.add(ProjectTemplateTable.teamId eq UUID.fromString(teamId))
        } else if (teamIds != null) {
            val uuids = teamIds.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
            conditions.add(ProjectTemplateTable.teamId inList uuids)
        }
        if (!search.isNullOrBlank()) {
            conditions.add(ProjectTemplateTable.name.lowerCase() like likeContains(search))
        }
        return conditions
    }

    override suspend fun findAll(
        teamId: String?,
        teamIds: List<String>?,
        offset: Int,
        limit: Int,
        search: String?,
    ): List<ProjectTemplate> = dbQuery {
        val conditions = buildProjectConditions(teamId, teamIds, search)
        val query = ProjectTemplateTable.selectAll()
        if (conditions.isNotEmpty()) {
            query.where { conditions.reduce { acc, op -> acc and op } }
        }
        query.orderBy(ProjectTemplateTable.updatedAt, SortOrder.DESC)
            .limit(limit)
            .offset(safeOffset(offset))
            .map { it.toProjectTemplate() }
    }

    override suspend fun countAll(teamId: String?, teamIds: List<String>?, search: String?): Long = dbQuery {
        val conditions = buildProjectConditions(teamId, teamIds, search)
        val query = ProjectTemplateTable.selectAll()
        if (conditions.isNotEmpty()) {
            query.where { conditions.reduce { acc, op -> acc and op } }
        }
        query.count()
    }

    // PROJ-H3: REPEATABLE_READ ensures count + data queries see consistent snapshot
    override suspend fun findAllWithCount(
        teamId: String?,
        teamIds: List<String>?,
        offset: Int,
        limit: Int,
        search: String?,
    ): Pair<List<ProjectTemplate>, Long> = dbQuery(Connection.TRANSACTION_REPEATABLE_READ) {
        val conditions = buildProjectConditions(teamId, teamIds, search)

        val countQuery = ProjectTemplateTable.selectAll()
        if (conditions.isNotEmpty()) {
            countQuery.where { conditions.reduce { acc, op -> acc and op } }
        }
        val totalCount = countQuery.count()

        val dataQuery = ProjectTemplateTable.selectAll()
        if (conditions.isNotEmpty()) {
            dataQuery.where { conditions.reduce { acc, op -> acc and op } }
        }
        val items = dataQuery.orderBy(ProjectTemplateTable.updatedAt, SortOrder.DESC)
            .limit(limit)
            .offset(safeOffset(offset))
            .map { it.toProjectTemplate() }

        items to totalCount
    }

    override suspend fun findById(id: ProjectId): ProjectTemplate? = dbQuery {
        ProjectTemplateTable.selectAll()
            .where { ProjectTemplateTable.id eq UUID.fromString(id.value) }
            .singleOrNull()
            ?.toProjectTemplate()
    }

    override suspend fun findTeamId(id: ProjectId): String? = dbQuery {
        ProjectTemplateTable.select(ProjectTemplateTable.teamId)
            .where { ProjectTemplateTable.id eq UUID.fromString(id.value) }
            .singleOrNull()
            ?.get(ProjectTemplateTable.teamId)?.value?.toString()
    }

    override suspend fun create(
        name: String,
        description: String,
        dagGraph: DagGraph,
        parameters: List<Parameter>,
        teamId: String,
        defaultTags: List<String>,
    ): ProjectTemplate = dbQuery {
        val now = Clock.System.now()
        val id = UUID.randomUUID()
        ProjectTemplateTable.insert {
            it[ProjectTemplateTable.id] = id
            it[ProjectTemplateTable.name] = name
            it[ProjectTemplateTable.description] = description
            it[ProjectTemplateTable.dagGraph] = dagGraph
            it[ProjectTemplateTable.parameters] = parameters
            it[ProjectTemplateTable.defaultTags] = defaultTags
            it[ProjectTemplateTable.teamId] = UUID.fromString(teamId)
            it[ProjectTemplateTable.createdAt] = now
            it[ProjectTemplateTable.updatedAt] = now
        }
        ProjectTemplate(
            id = ProjectId(id.toString()),
            name = name,
            description = description,
            dagGraph = dagGraph,
            parameters = parameters,
            defaultTags = defaultTags,
            createdAt = now,
            updatedAt = now,
        )
    }

    override suspend fun update(
        id: ProjectId,
        name: String?,
        description: String?,
        dagGraph: DagGraph?,
        parameters: List<Parameter>?,
        defaultTags: List<String>?,
    ): ProjectTemplate? = dbQuery {
        val uuid = UUID.fromString(id.value)
        val now = Clock.System.now()
        // todo claude: duplicate 16 lines
        val updated = ProjectTemplateTable.update({ ProjectTemplateTable.id eq uuid }) { stmt ->
            name?.let { stmt[ProjectTemplateTable.name] = it }
            description?.let { stmt[ProjectTemplateTable.description] = it }
            dagGraph?.let { stmt[ProjectTemplateTable.dagGraph] = it }
            parameters?.let { stmt[ProjectTemplateTable.parameters] = it }
            defaultTags?.let { stmt[ProjectTemplateTable.defaultTags] = it }
            stmt[ProjectTemplateTable.updatedAt] = now
        }
        if (updated > 0) {
            ProjectTemplateTable.selectAll()
                .where { ProjectTemplateTable.id eq uuid }
                .single()
                .toProjectTemplate()
        } else {
            null
        }
    }

    /**
     * PROJ-C1: Atomically check the project lock and update the project in a single transaction.
     * Prevents TOCTOU race where another user acquires the lock between the check and the update.
     */
    override suspend fun updateWithLockCheck(
        id: ProjectId,
        callerUserId: String,
        name: String?,
        description: String?,
        dagGraph: DagGraph?,
        parameters: List<Parameter>?,
        defaultTags: List<String>?,
    ): ProjectTemplate? = dbQuery {
        val uuid = UUID.fromString(id.value)
        val now = Clock.System.now()

        // Check for active lock by another user within the same transaction
        // forUpdate() prevents another transaction from modifying the lock between our read and the update
        val activeLock = ProjectLockTable.selectAll()
            .where {
                (ProjectLockTable.projectId eq uuid) and
                    (ProjectLockTable.expiresAt greater now)
            }
            .forUpdate()
            .singleOrNull()

        if (activeLock != null && activeLock[ProjectLockTable.userId].value.toString() != callerUserId) {
            throw LockConflictException(
                ProjectLockInfo(
                    userId = activeLock[ProjectLockTable.userId].value.toString(),
                    username = activeLock[ProjectLockTable.username],
                    acquiredAt = activeLock[ProjectLockTable.acquiredAt],
                    expiresAt = activeLock[ProjectLockTable.expiresAt],
                )
            )
        }

        // todo claude: duplicate 16 lines
        val updated = ProjectTemplateTable.update({ ProjectTemplateTable.id eq uuid }) { stmt ->
            name?.let { stmt[ProjectTemplateTable.name] = it }
            description?.let { stmt[ProjectTemplateTable.description] = it }
            dagGraph?.let { stmt[ProjectTemplateTable.dagGraph] = it }
            parameters?.let { stmt[ProjectTemplateTable.parameters] = it }
            defaultTags?.let { stmt[ProjectTemplateTable.defaultTags] = it }
            stmt[ProjectTemplateTable.updatedAt] = now
        }
        if (updated > 0) {
            ProjectTemplateTable.selectAll()
                .where { ProjectTemplateTable.id eq uuid }
                .single()
                .toProjectTemplate()
        } else {
            null
        }
    }

    override suspend fun delete(id: ProjectId): Boolean = dbQuery {
        val deleted = ProjectTemplateTable.deleteWhere {
            ProjectTemplateTable.id eq UUID.fromString(id.value)
        }
        deleted > 0
    }
}
