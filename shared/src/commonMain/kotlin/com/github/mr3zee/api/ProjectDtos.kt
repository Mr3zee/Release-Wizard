package com.github.mr3zee.api

import com.github.mr3zee.model.DagGraph
import com.github.mr3zee.model.Parameter
import com.github.mr3zee.model.ProjectTemplate
import com.github.mr3zee.model.TeamId
import kotlinx.serialization.Serializable

@Serializable
data class CreateProjectRequest(
    val name: String,
    val teamId: TeamId,
    val description: String = "",
    val dagGraph: DagGraph = DagGraph(),
    val parameters: List<Parameter> = emptyList(),
    val defaultTags: List<String> = emptyList(),
)

@Serializable
data class UpdateProjectRequest(
    val name: String? = null,
    val description: String? = null,
    val dagGraph: DagGraph? = null,
    val parameters: List<Parameter>? = null,
    val defaultTags: List<String>? = null,
)

@Serializable
data class ProjectResponse(
    val project: ProjectTemplate,
)

@Serializable
data class ProjectListResponse(
    val projects: List<ProjectTemplate>,
    val pagination: PaginationInfo? = null,
)
