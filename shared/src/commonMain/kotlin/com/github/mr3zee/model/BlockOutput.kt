package com.github.mr3zee.model

import kotlinx.serialization.Serializable

@Serializable
data class BlockOutput(
    val name: String,
    val description: String = "",
)
