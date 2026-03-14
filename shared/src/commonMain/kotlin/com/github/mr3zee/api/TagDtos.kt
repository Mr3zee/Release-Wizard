package com.github.mr3zee.api

import kotlinx.serialization.Serializable

@Serializable
data class TagListResponse(
    val tags: List<TagInfo>,
)

@Serializable
data class TagInfo(
    val name: String,
    val count: Long,
)

@Serializable
data class RenameTagRequest(
    val newName: String,
)
