package com.github.mr3zee.api

import com.github.mr3zee.model.Parameter
import com.github.mr3zee.model.ProjectId
import kotlinx.serialization.Serializable

@Serializable
data class CreateTriggerRequest(
    val parametersTemplate: List<Parameter> = emptyList(),
)

@Serializable
data class TriggerResponse(
    val id: String,
    val projectId: ProjectId,
    val secret: String,
    val enabled: Boolean,
    val webhookUrl: String,
)

@Serializable
data class TriggerListResponse(
    val triggers: List<TriggerResponse>,
)
