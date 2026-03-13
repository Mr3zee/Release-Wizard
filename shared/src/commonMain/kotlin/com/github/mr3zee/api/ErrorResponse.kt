package com.github.mr3zee.api

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val error: String,
    val code: String,
    val correlationId: String? = null,
)
