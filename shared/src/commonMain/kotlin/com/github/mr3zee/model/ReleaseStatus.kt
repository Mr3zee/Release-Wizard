package com.github.mr3zee.model

import kotlinx.serialization.Serializable

@Serializable
enum class ReleaseStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}
