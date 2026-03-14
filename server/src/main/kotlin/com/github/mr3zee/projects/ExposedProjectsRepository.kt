package com.github.mr3zee.projects

import com.github.mr3zee.model.*
import com.github.mr3zee.persistence.ProjectTemplateTable
import com.github.mr3zee.persistence.escapeLikePattern
import com.github.mr3zee.persistence.safeOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.UUID
import kotlin.time.Clock

class ExposedProjectsRepository(private val db: Database) : ProjectsRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction(db) { block() } }

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
        ownerId: String?,
        search: String?,
    ): List<Op<Boolean>> {
        val conditions = mutableListOf<Op<Boolean>>()
        if (ownerId != null) {
            conditions.add(ProjectTemplateTable.ownerId eq ownerId)
        }
        if (!search.isNullOrBlank()) {
            conditions.add(ProjectTemplateTable.name.lowerCase() like "%${escapeLikePattern(search.lowercase())}%")
        }
        return conditions
    }

    override suspend fun findAll(
        ownerId: String?,
        offset: Int,
        limit: Int,
        search: String?,
    ): List<ProjectTemplate> = dbQuery {
        val conditions = buildProjectConditions(ownerId, search)
        val query = ProjectTemplateTable.selectAll()
        if (conditions.isNotEmpty()) {
            query.where { conditions.reduce { acc, op -> acc and op } }
        }
        query.orderBy(ProjectTemplateTable.updatedAt, SortOrder.DESC)
            .limit(limit)
            .offset(safeOffset(offset))
            .map { it.toProjectTemplate() }
    }

    override suspend fun countAll(ownerId: String?, search: String?): Long = dbQuery {
        val conditions = buildProjectConditions(ownerId, search)
        val query = ProjectTemplateTable.selectAll()
        if (conditions.isNotEmpty()) {
            query.where { conditions.reduce { acc, op -> acc and op } }
        }
        query.count()
    }

    override suspend fun findAllWithCount(
        ownerId: String?,
        offset: Int,
        limit: Int,
        search: String?,
    ): Pair<List<ProjectTemplate>, Long> = dbQuery {
        val conditions = buildProjectConditions(ownerId, search)

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

    override suspend fun findOwner(id: ProjectId): String? = dbQuery {
        ProjectTemplateTable.select(ProjectTemplateTable.ownerId)
            .where { ProjectTemplateTable.id eq UUID.fromString(id.value) }
            .singleOrNull()
            ?.get(ProjectTemplateTable.ownerId)
    }

    override suspend fun create(
        name: String,
        description: String,
        dagGraph: DagGraph,
        parameters: List<Parameter>,
        ownerId: String,
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
            it[ProjectTemplateTable.ownerId] = ownerId
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
