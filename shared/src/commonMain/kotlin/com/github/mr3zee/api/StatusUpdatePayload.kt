package com.github.mr3zee.api

import kotlinx.serialization.Serializable

@Serializable
data class StatusUpdatePayload(
    val status: String,
    val description: String? = null,
)
