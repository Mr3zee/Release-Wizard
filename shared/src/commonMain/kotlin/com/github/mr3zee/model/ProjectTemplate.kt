package com.github.mr3zee.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ProjectTemplate(
    val id: ProjectId,
    val name: String,
    val description: String = "",
    val dagGraph: DagGraph = DagGraph(),
    val parameters: List<Parameter> = emptyList(),
    val defaultTags: List<String> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant,
)
