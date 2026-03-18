package com.github.mr3zee.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class MavenTrigger(
    val id: String,
    val projectId: ProjectId,
    val repoUrl: String,
    val groupId: String,
    val artifactId: String,
    val parameterKey: String,
    val enabled: Boolean,
    val includeSnapshots: Boolean,
    val lastCheckedAt: Instant?,
)
