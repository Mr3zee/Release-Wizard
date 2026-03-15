package com.github.mr3zee.triggers

import com.github.mr3zee.model.Parameter
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.persistence.TriggerTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.UUID

class ExposedTriggerRepository(private val db: Database) : TriggerRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction(db) { block() } }

    private fun ResultRow.toEntity(): TriggerEntity {
        return TriggerEntity(
            id = this[TriggerTable.id].value.toString(),
            projectId = ProjectId(this[TriggerTable.projectId].value.toString()),
            secret = this[TriggerTable.secret],
            enabled = this[TriggerTable.enabled],
            parametersTemplate = this[TriggerTable.parametersTemplate],
        )
    }

    override suspend fun findByProjectId(projectId: ProjectId): List<TriggerEntity> = dbQuery {
        TriggerTable.selectAll()
            .where { TriggerTable.projectId eq UUID.fromString(projectId.value) }
            .map { it.toEntity() }
    }

    override suspend fun findById(id: String): TriggerEntity? = dbQuery {
        TriggerTable.selectAll()
            .where { TriggerTable.id eq UUID.fromString(id) }
            .singleOrNull()
            ?.toEntity()
    }

    override suspend fun create(
        projectId: ProjectId,
        secret: String,
        parametersTemplate: List<Parameter>,
    ): TriggerEntity = dbQuery {
        val id = UUID.randomUUID()
        TriggerTable.insert {
            it[TriggerTable.id] = id
            it[TriggerTable.projectId] = UUID.fromString(projectId.value)
            it[TriggerTable.secret] = secret
            it[TriggerTable.enabled] = true
            it[TriggerTable.parametersTemplate] = parametersTemplate
        }
        TriggerEntity(
            id = id.toString(),
            projectId = projectId,
            secret = secret,
            enabled = true,
            parametersTemplate = parametersTemplate,
        )
    }

    override suspend fun update(id: String, enabled: Boolean?): TriggerEntity? = dbQuery {
        val uuid = UUID.fromString(id)
        val updated = TriggerTable.update({ TriggerTable.id eq uuid }) { stmt ->
            enabled?.let { stmt[TriggerTable.enabled] = it }
        }
        if (updated > 0) {
            TriggerTable.selectAll()
                .where { TriggerTable.id eq uuid }
                .single()
                .toEntity()
        } else {
            null
        }
    }

    override suspend fun delete(id: String): Boolean = dbQuery {
        val deleted = TriggerTable.deleteWhere {
            TriggerTable.id eq UUID.fromString(id)
        }
        deleted > 0
    }
}
