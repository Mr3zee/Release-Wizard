package com.github.mr3zee.api

import com.github.mr3zee.model.UserNotification
import kotlinx.serialization.Serializable

@Serializable
data class UserNotificationListResponse(
    val notifications: List<UserNotification>,
    val pagination: PaginationInfo,
)

@Serializable
data class UnreadCountResponse(
    val count: Long,
)
