package com.github.mr3zee.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mr3zee.api.ConnectionApiClient
import com.github.mr3zee.api.toUserMessage
import com.github.mr3zee.api.CreateConnectionRequest
import com.github.mr3zee.api.PaginationInfo
import com.github.mr3zee.api.UpdateConnectionRequest
import com.github.mr3zee.model.Connection
import com.github.mr3zee.model.ConnectionConfig
import com.github.mr3zee.model.ConnectionId
import com.github.mr3zee.model.ConnectionType
import com.github.mr3zee.model.TeamId
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
class ConnectionsViewModel(
    private val apiClient: ConnectionApiClient,
    private val activeTeamId: StateFlow<TeamId?>,
) : ViewModel() {

    private val _connections = MutableStateFlow<List<Connection>>(emptyList())
    val connections: StateFlow<List<Connection>> = _connections

    private val _webhookUrls = MutableStateFlow<Map<String, String>>(emptyMap())
    val webhookUrls: StateFlow<Map<String, String>> = _webhookUrls

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _editingConnection = MutableStateFlow<Connection?>(null)
    val editingConnection: StateFlow<Connection?> = _editingConnection

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _typeFilter = MutableStateFlow<ConnectionType?>(null)
    val typeFilter: StateFlow<ConnectionType?> = _typeFilter

    private val _pagination = MutableStateFlow<PaginationInfo?>(null)
    val pagination: StateFlow<PaginationInfo?> = _pagination

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private val _testSuccessMessage = MutableStateFlow<String?>(null)
    val testSuccessMessage: StateFlow<String?> = _testSuccessMessage

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    private val _savedSuccessfully = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val savedSuccessfully: SharedFlow<Unit> = _savedSuccessfully

    private val pageSize = 20

    init {
        viewModelScope.launch {
            combine(
                _searchQuery.debounce(300.milliseconds),
                _typeFilter,
            ) { search, type -> search to type }
                .distinctUntilChanged()
                .collectLatest {
                    loadConnectionsInternal(reset = true)
                }
        }
        viewModelScope.launch {
            activeTeamId.filterNotNull().distinctUntilChanged().collectLatest {
                loadConnectionsInternal(reset = true)
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setTypeFilter(type: ConnectionType?) {
        _typeFilter.value = type
    }

    fun loadConnections() {
        viewModelScope.launch {
            loadConnectionsInternal(reset = true)
        }
    }

    fun loadMore() {
        val nextOffset = _pagination.value?.nextPageOffset() ?: return
        if (_isLoadingMore.value) return

        // Capture current filter state to detect changes during in-flight request
        val currentSearch = _searchQuery.value
        val currentType = _typeFilter.value

        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val response = apiClient.listConnections(
                    offset = nextOffset,
                    limit = pageSize,
                    search = currentSearch.takeIf { it.isNotBlank() },
                    type = currentType?.name,
                )
                // Only append if filters haven't changed while we were loading
                if (_searchQuery.value == currentSearch && _typeFilter.value == currentType) {
                    _connections.value += response.connections
                    _webhookUrls.value += response.webhookUrls
                    _pagination.value = response.pagination
                }
            } catch (e: Exception) {
                _error.value = e.toUserMessage()
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    private suspend fun loadConnectionsInternal(reset: Boolean) {
        if (reset) {
            _isLoading.value = true
            _error.value = null
        }
        try {
            val response = apiClient.listConnections(
                offset = 0,
                limit = pageSize,
                search = _searchQuery.value.takeIf { it.isNotBlank() },
                type = _typeFilter.value?.name,
            )
            _connections.value = response.connections
            _webhookUrls.value = response.webhookUrls
            _pagination.value = response.pagination
        } catch (e: Exception) {
            _error.value = e.toUserMessage()
        } finally {
            _isLoading.value = false
        }
    }

    fun createConnection(name: String, type: ConnectionType, config: ConnectionConfig) {
        viewModelScope.launch {
            _error.value = null
            _isSaving.value = true
            try {
                val teamId = activeTeamId.value ?: error("No active team selected")
                apiClient.createConnection(CreateConnectionRequest(name = name, teamId = teamId, type = type, config = config))
                loadConnections()
                _savedSuccessfully.tryEmit(Unit)
            } catch (e: Exception) {
                _error.value = e.toUserMessage()
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun loadConnection(id: ConnectionId) {
        viewModelScope.launch {
            _error.value = null
            try {
                _editingConnection.value = apiClient.getConnection(id)
            } catch (e: Exception) {
                _error.value = e.toUserMessage()
            }
        }
    }

    fun clearEditingConnection() {
        _editingConnection.value = null
    }

    fun updateConnection(id: ConnectionId, name: String?, config: ConnectionConfig?) {
        viewModelScope.launch {
            _error.value = null
            _isSaving.value = true
            try {
                apiClient.updateConnection(id, UpdateConnectionRequest(name = name, config = config))
                loadConnections()
                _savedSuccessfully.tryEmit(Unit)
            } catch (e: Exception) {
                _error.value = e.toUserMessage()
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun deleteConnection(id: ConnectionId) {
        viewModelScope.launch {
            _error.value = null
            try {
                apiClient.deleteConnection(id)
                loadConnections()
            } catch (e: Exception) {
                _error.value = e.toUserMessage()
            }
        }
    }

    fun testConnection(id: ConnectionId) {
        viewModelScope.launch {
            _error.value = null
            try {
                val result = apiClient.testConnection(id)
                if (result.success) {
                    _testSuccessMessage.value = "Connection test succeeded: ${result.message}"
                } else {
                    _error.value = "Test failed: ${result.message}"
                }
            } catch (e: Exception) {
                _error.value = e.toUserMessage()
            }
        }
    }

    fun clearTestSuccessMessage() {
        _testSuccessMessage.value = null
    }

    fun dismissError() {
        _error.value = null
    }
}
