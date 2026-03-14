package com.github.mr3zee.projects

import com.github.mr3zee.model.DagGraph
import com.github.mr3zee.model.Parameter
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.model.ProjectTemplate

interface ProjectsRepository {
    suspend fun findAll(
        teamId: String? = null,
        teamIds: List<String>? = null,
        offset: Int = 0,
        limit: Int = 20,
        search: String? = null,
    ): List<ProjectTemplate>
    suspend fun countAll(teamId: String? = null, teamIds: List<String>? = null, search: String? = null): Long
    suspend fun findAllWithCount(
        teamId: String? = null,
        teamIds: List<String>? = null,
        offset: Int = 0,
        limit: Int = 20,
        search: String? = null,
    ): Pair<List<ProjectTemplate>, Long>
    suspend fun findById(id: ProjectId): ProjectTemplate?
    suspend fun findTeamId(id: ProjectId): String?
    suspend fun create(name: String, description: String, dagGraph: DagGraph, parameters: List<Parameter>, teamId: String, defaultTags: List<String> = emptyList()): ProjectTemplate
    suspend fun update(id: ProjectId, name: String?, description: String?, dagGraph: DagGraph?, parameters: List<Parameter>?, defaultTags: List<String>? = null): ProjectTemplate?
    suspend fun delete(id: ProjectId): Boolean
}
