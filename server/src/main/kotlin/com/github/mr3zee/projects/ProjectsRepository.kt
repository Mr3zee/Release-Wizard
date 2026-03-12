package com.github.mr3zee.projects

import com.github.mr3zee.model.DagGraph
import com.github.mr3zee.model.Parameter
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.model.ProjectTemplate

interface ProjectsRepository {
    suspend fun findAll(): List<ProjectTemplate>
    suspend fun findById(id: ProjectId): ProjectTemplate?
    suspend fun create(name: String, description: String, dagGraph: DagGraph, parameters: List<Parameter>): ProjectTemplate
    suspend fun update(id: ProjectId, name: String?, description: String?, dagGraph: DagGraph?, parameters: List<Parameter>?): ProjectTemplate?
    suspend fun delete(id: ProjectId): Boolean
}
