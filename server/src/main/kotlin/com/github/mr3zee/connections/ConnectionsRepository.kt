package com.github.mr3zee.connections

import com.github.mr3zee.model.Connection
import com.github.mr3zee.model.ConnectionConfig
import com.github.mr3zee.model.ConnectionId
import com.github.mr3zee.model.ConnectionType

interface ConnectionsRepository {
    suspend fun findAll(
        teamId: String? = null,
        teamIds: List<String>? = null,
        offset: Int = 0,
        limit: Int = 20,
        search: String? = null,
        type: ConnectionType? = null,
    ): List<Connection>
    suspend fun countAll(
        teamId: String? = null,
        teamIds: List<String>? = null,
        search: String? = null,
        type: ConnectionType? = null,
    ): Long
    suspend fun findAllWithCount(
        teamId: String? = null,
        teamIds: List<String>? = null,
        offset: Int = 0,
        limit: Int = 20,
        search: String? = null,
        type: ConnectionType? = null,
    ): Pair<List<Connection>, Long>
    suspend fun findById(id: ConnectionId): Connection?
    suspend fun findTeamId(id: ConnectionId): String?
    suspend fun findTeamIds(ids: List<ConnectionId>): Map<ConnectionId, String> {
        val result = mutableMapOf<ConnectionId, String>()
        for (id in ids) {
            val teamId = findTeamId(id)
            if (teamId != null) {
                result[id] = teamId
            }
        }
        return result
    }
    suspend fun create(name: String, type: ConnectionType, config: ConnectionConfig, teamId: String): Connection
    suspend fun update(id: ConnectionId, name: String?, config: ConnectionConfig?): Connection?
    suspend fun delete(id: ConnectionId): Boolean
}
