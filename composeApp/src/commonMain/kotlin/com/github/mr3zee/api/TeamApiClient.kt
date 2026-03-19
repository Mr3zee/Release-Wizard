package com.github.mr3zee.api

import com.github.mr3zee.model.JoinRequest
import com.github.mr3zee.model.TeamId
import com.github.mr3zee.model.TeamInvite
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class TeamApiClient(private val client: HttpClient) {

    suspend fun createTeam(request: CreateTeamRequest): TeamResponse {
        val response = client.post(serverUrl(ApiRoutes.Teams.BASE)) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return response.body()
    }

    suspend fun listTeams(offset: Int = 0, limit: Int = 20, search: String? = null): TeamListResponse {
        val response = client.get(serverUrl(ApiRoutes.Teams.BASE)) {
            parameter("offset", offset)
            parameter("limit", limit)
            if (search != null) parameter("q", search)
        }
        return response.body()
    }

    suspend fun getTeamDetail(teamId: TeamId): TeamDetailResponse {
        val response = client.get(serverUrl(ApiRoutes.Teams.byId(teamId.value)))
        return response.body()
    }

    suspend fun updateTeam(teamId: TeamId, request: UpdateTeamRequest): TeamResponse {
        val response = client.put(serverUrl(ApiRoutes.Teams.byId(teamId.value))) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return response.body()
    }

    suspend fun deleteTeam(teamId: TeamId) {
        client.delete(serverUrl(ApiRoutes.Teams.byId(teamId.value)))
    }

    // Members

    suspend fun listMembers(teamId: TeamId): TeamMemberListResponse {
        val response = client.get(serverUrl(ApiRoutes.Teams.members(teamId.value)))
        return response.body()
    }

    suspend fun updateMemberRole(teamId: TeamId, userId: String, request: UpdateMemberRoleRequest) {
        client.put(serverUrl(ApiRoutes.Teams.member(teamId.value, userId))) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun removeMember(teamId: TeamId, userId: String) {
        client.delete(serverUrl(ApiRoutes.Teams.member(teamId.value, userId)))
    }

    suspend fun leaveTeam(teamId: TeamId) {
        client.post(serverUrl(ApiRoutes.Teams.leave(teamId.value)))
    }

    // Invites

    suspend fun inviteUser(teamId: TeamId, username: String): TeamInvite {
        val response = client.post(serverUrl(ApiRoutes.Teams.invites(teamId.value))) {
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(username = username))
        }
        return response.body()
    }

    suspend fun listTeamInvites(teamId: TeamId): InviteListResponse {
        val response = client.get(serverUrl(ApiRoutes.Teams.invites(teamId.value)))
        return response.body()
    }

    suspend fun cancelInvite(teamId: TeamId, inviteId: String) {
        client.delete(serverUrl(ApiRoutes.Teams.invite(teamId.value, inviteId)))
    }

    // Join Requests

    suspend fun submitJoinRequest(teamId: TeamId): JoinRequest {
        val response = client.post(serverUrl(ApiRoutes.Teams.joinRequests(teamId.value)))
        return response.body()
    }

    suspend fun listJoinRequests(teamId: TeamId): JoinRequestListResponse {
        val response = client.get(serverUrl(ApiRoutes.Teams.joinRequests(teamId.value)))
        return response.body()
    }

    suspend fun approveJoinRequest(teamId: TeamId, requestId: String) {
        client.post(serverUrl(ApiRoutes.Teams.approveJoinRequest(teamId.value, requestId)))
    }

    suspend fun rejectJoinRequest(teamId: TeamId, requestId: String) {
        client.post(serverUrl(ApiRoutes.Teams.rejectJoinRequest(teamId.value, requestId)))
    }

    // My Invites

    suspend fun getMyInvites(): InviteListResponse {
        val response = client.get(serverUrl(ApiRoutes.Auth.MyInvites.BASE))
        return response.body()
    }

    suspend fun acceptInvite(inviteId: String) {
        client.post(serverUrl(ApiRoutes.Auth.MyInvites.accept(inviteId)))
    }

    suspend fun declineInvite(inviteId: String) {
        client.post(serverUrl(ApiRoutes.Auth.MyInvites.decline(inviteId)))
    }

    // Audit

    suspend fun getAuditLog(teamId: TeamId, offset: Int = 0, limit: Int = 50): AuditEventListResponse {
        val response = client.get(serverUrl(ApiRoutes.Teams.audit(teamId.value))) {
            parameter("offset", offset)
            parameter("limit", limit)
        }
        return response.body()
    }
}
