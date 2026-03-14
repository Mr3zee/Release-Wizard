package com.github.mr3zee.api

import kotlinx.serialization.Serializable

@Serializable
data class PaginationInfo(
    val totalCount: Long,
    val offset: Int,
    val limit: Int,
)
