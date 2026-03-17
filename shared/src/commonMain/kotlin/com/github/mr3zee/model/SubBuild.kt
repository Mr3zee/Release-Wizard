package com.github.mr3zee.model

import kotlinx.serialization.Serializable

@Serializable
data class SubBuild(
    val id: String,
    val name: String,
    val status: SubBuildStatus,
    val buildUrl: String? = null,
    val durationSeconds: Long? = null,
    val dependencyLevel: Int = 0,
)

@Serializable
enum class SubBuildStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    UNKNOWN,
}
