package com.github.mr3zee.projects

import com.github.mr3zee.model.*
import com.github.mr3zee.persistence.ProjectTemplateTable
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
            createdAt = this[ProjectTemplateTable.createdAt],
            updatedAt = this[ProjectTemplateTable.updatedAt],
        )
    }

    override suspend fun findAll(): List<ProjectTemplate> = dbQuery {
        ProjectTemplateTable.selectAll()
            .orderBy(ProjectTemplateTable.updatedAt, SortOrder.DESC)
            .map { it.toProjectTemplate() }
    }

    override suspend fun findById(id: ProjectId): ProjectTemplate? = dbQuery {
        ProjectTemplateTable.selectAll()
            .where { ProjectTemplateTable.id eq UUID.fromString(id.value) }
            .singleOrNull()
            ?.toProjectTemplate()
    }

    override suspend fun create(
        name: String,
        description: String,
        dagGraph: DagGraph,
        parameters: List<Parameter>,
    ): ProjectTemplate = dbQuery {
        val now = Clock.System.now()
        val id = UUID.randomUUID()
        ProjectTemplateTable.insert {
            it[ProjectTemplateTable.id] = id
            it[ProjectTemplateTable.name] = name
            it[ProjectTemplateTable.description] = description
            it[ProjectTemplateTable.dagGraph] = dagGraph
            it[ProjectTemplateTable.parameters] = parameters
            it[ProjectTemplateTable.createdAt] = now
            it[ProjectTemplateTable.updatedAt] = now
        }
        ProjectTemplate(
            id = ProjectId(id.toString()),
            name = name,
            description = description,
            dagGraph = dagGraph,
            parameters = parameters,
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
    ): ProjectTemplate? = dbQuery {
        val uuid = UUID.fromString(id.value)
        val now = Clock.System.now()
        val updated = ProjectTemplateTable.update({ ProjectTemplateTable.id eq uuid }) { stmt ->
            name?.let { stmt[ProjectTemplateTable.name] = it }
            description?.let { stmt[ProjectTemplateTable.description] = it }
            dagGraph?.let { stmt[ProjectTemplateTable.dagGraph] = it }
            parameters?.let { stmt[ProjectTemplateTable.parameters] = it }
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
