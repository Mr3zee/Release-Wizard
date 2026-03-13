package com.github.mr3zee.model

import kotlinx.serialization.Serializable

@Serializable
enum class ReleaseStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    ARCHIVED,
}

val ReleaseStatus.isTerminal: Boolean
    get() = this == ReleaseStatus.SUCCEEDED ||
            this == ReleaseStatus.FAILED ||
            this == ReleaseStatus.CANCELLED ||
            this == ReleaseStatus.ARCHIVED
