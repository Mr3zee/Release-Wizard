package com.github.mr3zee.model

import kotlinx.serialization.Serializable

@Serializable
data class Schedule(
    val id: String,
    val projectId: ProjectId,
    val cronExpression: String,
    val parameters: List<Parameter> = emptyList(),
    val enabled: Boolean = true,
)
