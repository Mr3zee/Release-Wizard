package com.github.mr3zee.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Release(
    val id: ReleaseId,
    val projectTemplateId: ProjectId,
    val status: ReleaseStatus,
    val dagSnapshot: DagGraph,
    val parameters: List<Parameter> = emptyList(),
    val startedAt: Instant? = null,
    val finishedAt: Instant? = null,
)
