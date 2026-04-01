package com.github.mr3zee.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mr3zee.api.PaginationInfo
import com.github.mr3zee.api.UserNotificationApiClient
import com.github.mr3zee.api.toUiMessage
import com.github.mr3zee.model.UserNotification
import com.github.mr3zee.util.UiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class NotificationsViewModel(
    private val apiClient: UserNotificationApiClient,
    private val onUnreadCountChanged: (Long) -> Unit = {},
) : ViewModel() {

    private val _notifications = MutableStateFlow<List<UserNotification>>(emptyList())
    val notifications: StateFlow<List<UserNotification>> = _notifications

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<UiMessage?>(null)
    val error: StateFlow<UiMessage?> = _error

    private val _pagination = MutableStateFlow<PaginationInfo?>(null)
    val pagination: StateFlow<PaginationInfo?> = _pagination

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _isManualRefresh = MutableStateFlow(false)
    val isManualRefresh: StateFlow<Boolean> = _isManualRefresh

    private val _refreshError = MutableStateFlow<UiMessage?>(null)
    val refreshError: StateFlow<UiMessage?> = _refreshError

    private val pageSize = 30
    private var hasLoaded = false

    fun loadIfNeeded() {
        if (!hasLoaded) {
            hasLoaded = true
            loadNotifications()
        }
    }

    private fun loadNotifications() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = apiClient.listNotifications(offset = 0, limit = pageSize)
                _notifications.value = response.notifications
                _pagination.value = response.pagination
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isManualRefresh.value = true
            _isRefreshing.value = true
            _refreshError.value = null
            try {
                val response = apiClient.listNotifications(offset = 0, limit = pageSize)
                _notifications.value = response.notifications
                _pagination.value = response.pagination
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _refreshError.value = e.toUiMessage()
            } finally {
                _isRefreshing.value = false
                _isManualRefresh.value = false
            }
        }
    }

    fun loadMore() {
        if (_isLoadingMore.value || _isRefreshing.value) return
        val nextOffset = _pagination.value?.nextPageOffset() ?: return
        _isLoadingMore.value = true
        viewModelScope.launch {
            try {
                val response = apiClient.listNotifications(offset = nextOffset, limit = pageSize)
                _notifications.value += response.notifications
                _pagination.value = response.pagination
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun markAsRead(notificationId: String) {
        _notifications.value = _notifications.value.map {
            if (it.id == notificationId) it.copy(read = true) else it
        }
        onUnreadCountChanged(_notifications.value.count { !it.read }.toLong())
        viewModelScope.launch {
            try {
                apiClient.markAsRead(notificationId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Revert: set this notification back to unread
                _notifications.value = _notifications.value.map {
                    if (it.id == notificationId) it.copy(read = false) else it
                }
                onUnreadCountChanged(_notifications.value.count { !it.read }.toLong())
            }
        }
    }

    fun markAllAsRead() {
        val unreadIds = _notifications.value.filter { !it.read }.map { it.id }.toSet()
        _notifications.value = _notifications.value.map { it.copy(read = true) }
        onUnreadCountChanged(0)
        viewModelScope.launch {
            try {
                apiClient.markAllAsRead()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Revert: restore original read state for the items that were unread
                _notifications.value = _notifications.value.map {
                    if (it.id in unreadIds) it.copy(read = false) else it
                }
                onUnreadCountChanged(_notifications.value.count { !it.read }.toLong())
            }
        }
    }

    fun deleteNotification(notificationId: String) {
        val removed = _notifications.value.find { it.id == notificationId } ?: return
        val removedIndex = _notifications.value.indexOf(removed)
        _notifications.value = _notifications.value.filter { it.id != notificationId }
        onUnreadCountChanged(_notifications.value.count { !it.read }.toLong())
        viewModelScope.launch {
            try {
                apiClient.deleteNotification(notificationId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Revert: re-insert the removed item at its original position
                val current = _notifications.value.toMutableList()
                current.add(removedIndex.coerceAtMost(current.size), removed)
                _notifications.value = current
                onUnreadCountChanged(_notifications.value.count { !it.read }.toLong())
            }
        }
    }

    fun deleteAllRead() {
        val removedItems = _notifications.value.filter { it.read }
        _notifications.value = _notifications.value.filter { !it.read }
        viewModelScope.launch {
            try {
                apiClient.deleteAllRead()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Revert: merge removed items back, sort by timestamp descending
                _notifications.value = (_notifications.value + removedItems)
                    .sortedByDescending { it.timestamp }
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }

    fun dismissRefreshError() {
        _refreshError.value = null
    }
}
