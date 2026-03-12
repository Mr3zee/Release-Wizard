package com.github.mr3zee.model

import kotlinx.serialization.Serializable

@Serializable
data class Parameter(
    val key: String,
    val value: String,
    val description: String = "",
)
