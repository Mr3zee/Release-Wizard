package com.github.mr3zee.api

import com.github.mr3zee.model.DagGraph
import com.github.mr3zee.model.Parameter
import com.github.mr3zee.model.ProjectTemplate
import kotlinx.serialization.Serializable

@Serializable
data class CreateProjectRequest(
    val name: String,
    val description: String = "",
    val dagGraph: DagGraph = DagGraph(),
    val parameters: List<Parameter> = emptyList(),
)

@Serializable
data class UpdateProjectRequest(
    val name: String? = null,
    val description: String? = null,
    val dagGraph: DagGraph? = null,
    val parameters: List<Parameter>? = null,
)

@Serializable
data class ProjectResponse(
    val project: ProjectTemplate,
)

@Serializable
data class ProjectListResponse(
    val projects: List<ProjectTemplate>,
)
