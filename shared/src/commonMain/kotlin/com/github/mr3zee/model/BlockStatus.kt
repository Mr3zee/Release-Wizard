package com.github.mr3zee.model

import kotlinx.serialization.Serializable

@Serializable
enum class BlockStatus {
    WAITING,
    RUNNING,
    FAILED,
    SUCCEEDED,
    WAITING_FOR_INPUT,
    STOPPED,
}
