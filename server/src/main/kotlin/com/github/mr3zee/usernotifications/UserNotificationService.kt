package com.github.mr3zee.usernotifications

import com.github.mr3zee.api.PaginationInfo
import com.github.mr3zee.api.UnreadCountResponse
import com.github.mr3zee.api.UserNotificationListResponse
import com.github.mr3zee.model.UserNotification

interface UserNotificationService {
    suspend fun listNotifications(userId: String, offset: Int, limit: Int): UserNotificationListResponse
    suspend fun getUnreadCount(userId: String): UnreadCountResponse
    suspend fun markAsRead(notificationId: String, userId: String): Boolean
    suspend fun markAllAsRead(userId: String): Int
}

class DefaultUserNotificationService(
    private val repository: UserNotificationRepository,
) : UserNotificationService {

    override suspend fun listNotifications(userId: String, offset: Int, limit: Int): UserNotificationListResponse {
        val (notifications, totalCount) = repository.findByUserWithCount(userId, offset, limit)
        return UserNotificationListResponse(
            notifications = notifications,
            pagination = PaginationInfo(totalCount = totalCount, offset = offset, limit = limit),
        )
    }

    override suspend fun getUnreadCount(userId: String): UnreadCountResponse {
        return UnreadCountResponse(count = repository.countUnread(userId))
    }

    override suspend fun markAsRead(notificationId: String, userId: String): Boolean {
        return repository.markAsRead(notificationId, userId)
    }

    override suspend fun markAllAsRead(userId: String): Int {
        return repository.markAllAsRead(userId)
    }
}
