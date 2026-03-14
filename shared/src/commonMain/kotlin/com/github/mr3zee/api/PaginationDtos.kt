package com.github.mr3zee.api

import kotlinx.serialization.Serializable

@Serializable
data class PaginationInfo(
    val totalCount: Long,
    val offset: Int,
    val limit: Int,
) {
    /** Returns the offset for the next page, or `null` if there are no more pages. */
    fun nextPageOffset(): Int? {
        val next = offset + limit
        return if (next < totalCount) next else null
    }
}
