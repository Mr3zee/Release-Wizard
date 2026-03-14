package com.github.mr3zee.model

import kotlinx.serialization.Serializable

@Serializable
data class Team(
    val id: TeamId,
    val name: String,
    val description: String = "",
    val createdAt: Long = 0,
)
