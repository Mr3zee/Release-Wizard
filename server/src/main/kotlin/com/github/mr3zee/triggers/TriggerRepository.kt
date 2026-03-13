package com.github.mr3zee.triggers

import com.github.mr3zee.model.Parameter
import com.github.mr3zee.model.ProjectId

data class TriggerEntity(
    val id: String,
    val projectId: ProjectId,
    val secret: String,
    val enabled: Boolean,
    val parametersTemplate: List<Parameter>,
)

interface TriggerRepository {
    suspend fun findByProjectId(projectId: ProjectId): List<TriggerEntity>
    suspend fun findById(id: String): TriggerEntity?
    suspend fun create(
        projectId: ProjectId,
        secret: String,
        parametersTemplate: List<Parameter>,
    ): TriggerEntity
    suspend fun update(id: String, enabled: Boolean?): TriggerEntity?
    suspend fun delete(id: String): Boolean
}
