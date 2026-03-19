package com.github.mr3zee.model

import kotlinx.serialization.Serializable

@Serializable
data class Parameter(
    val key: String,
    val value: String,
    /** Human-friendly description from external systems (TC/GH). Display-only; ignored by the execution engine. */
    val description: String = "",
    /** Human-friendly display name from external systems (TC/GH). Display-only; ignored by the execution engine. */
    val label: String = "",
)
