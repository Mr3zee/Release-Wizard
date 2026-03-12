package com.github.mr3zee.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mr3zee.api.ConnectionApiClient
import com.github.mr3zee.api.CreateConnectionRequest
import com.github.mr3zee.api.UpdateConnectionRequest
import com.github.mr3zee.model.Connection
import com.github.mr3zee.model.ConnectionConfig
import com.github.mr3zee.model.ConnectionId
import com.github.mr3zee.model.ConnectionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ConnectionsViewModel(
    private val apiClient: ConnectionApiClient,
) : ViewModel() {

    private val _connections = MutableStateFlow<List<Connection>>(emptyList())
    val connections: StateFlow<List<Connection>> = _connections

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun loadConnections() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _connections.value = apiClient.listConnections()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load connections"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createConnection(name: String, type: ConnectionType, config: ConnectionConfig) {
        viewModelScope.launch {
            _error.value = null
            try {
                apiClient.createConnection(CreateConnectionRequest(name = name, type = type, config = config))
                loadConnections()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to create connection"
            }
        }
    }

    fun updateConnection(id: ConnectionId, name: String?, config: ConnectionConfig?) {
        viewModelScope.launch {
            _error.value = null
            try {
                apiClient.updateConnection(id, UpdateConnectionRequest(name = name, config = config))
                loadConnections()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to update connection"
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
                _error.value = e.message ?: "Failed to delete connection"
            }
        }
    }

    fun testConnection(id: ConnectionId) {
        viewModelScope.launch {
            _error.value = null
            try {
                val result = apiClient.testConnection(id)
                if (!result.success) {
                    _error.value = "Test failed: ${result.message}"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Test failed"
            }
        }
    }
}
