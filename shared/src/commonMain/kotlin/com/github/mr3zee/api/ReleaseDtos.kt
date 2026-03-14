package com.github.mr3zee.api

import com.github.mr3zee.model.*
import kotlinx.serialization.Serializable

@Serializable
data class CreateReleaseRequest(
    val projectTemplateId: ProjectId,
    val parameters: List<Parameter> = emptyList(),
    val tags: List<String> = emptyList(),
)

@Serializable
data class ReleaseResponse(
    val release: Release,
    val blockExecutions: List<BlockExecution> = emptyList(),
)

@Serializable
data class ReleaseListResponse(
    val releases: List<Release>,
    val pagination: PaginationInfo? = null,
)

@Serializable
data class ApproveBlockRequest(
    val input: Map<String, String> = emptyMap(),
)
