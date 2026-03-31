package com.github.mr3zee.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class UserNotificationApiClient(private val client: HttpClient) {

    suspend fun listNotifications(offset: Int = 0, limit: Int = 20): UserNotificationListResponse {
        val response = client.get(serverUrl(ApiRoutes.UserNotifications.BASE)) {
            parameter("offset", offset)
            parameter("limit", limit)
        }
        return response.body()
    }

    suspend fun getUnreadCount(): UnreadCountResponse {
        val response = client.get(serverUrl(ApiRoutes.UserNotifications.UNREAD_COUNT))
        return response.body()
    }

    suspend fun markAsRead(notificationId: String) {
        client.post(serverUrl(ApiRoutes.UserNotifications.markRead(notificationId)))
    }

    suspend fun markAllAsRead() {
        client.post(serverUrl(ApiRoutes.UserNotifications.MARK_ALL_READ))
    }

    suspend fun deleteNotification(notificationId: String) {
        client.delete(serverUrl(ApiRoutes.UserNotifications.byId(notificationId)))
    }

    suspend fun deleteAllRead() {
        client.delete(serverUrl(ApiRoutes.UserNotifications.DELETE_ALL_READ))
    }
}
