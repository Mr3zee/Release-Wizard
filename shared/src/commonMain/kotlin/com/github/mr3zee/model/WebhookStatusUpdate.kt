package com.github.mr3zee.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class WebhookStatusUpdate(
    val status: String,
    val description: String? = null,
    val receivedAt: Instant,
)
