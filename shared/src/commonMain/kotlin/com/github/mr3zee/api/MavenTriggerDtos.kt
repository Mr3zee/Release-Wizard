package com.github.mr3zee.api

import com.github.mr3zee.model.MavenTrigger
import kotlinx.serialization.Serializable

@Serializable
data class CreateMavenTriggerRequest(
    val repoUrl: String,
    val groupId: String,
    val artifactId: String,
    val parameterKey: String,
    val includeSnapshots: Boolean = false,
    val enabled: Boolean = true,
)

@Serializable
data class MavenTriggerResponse(
    val trigger: MavenTrigger,
)

@Serializable
data class MavenTriggerListResponse(
    val triggers: List<MavenTrigger>,
)

@Serializable
data class ToggleMavenTriggerRequest(val enabled: Boolean)
