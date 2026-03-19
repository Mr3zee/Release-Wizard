package com.github.mr3zee.teams

import com.github.mr3zee.ForbiddenException
import com.github.mr3zee.NotFoundException
import com.github.mr3zee.api.*
import com.github.mr3zee.audit.AuditService
import com.github.mr3zee.auth.UserSession
import com.github.mr3zee.model.*

interface TeamService {
    suspend fun createTeam(request: CreateTeamRequest, session: UserSession): TeamResponse
    suspend fun listTeams(offset: Int, limit: Int, search: String?): TeamListResponse
    suspend fun getTeamDetail(teamId: TeamId, session: UserSession): TeamDetailResponse
    suspend fun updateTeam(teamId: TeamId, request: UpdateTeamRequest, session: UserSession): TeamResponse
    suspend fun deleteTeam(teamId: TeamId, session: UserSession)

    suspend fun listMembers(teamId: TeamId, session: UserSession): TeamMemberListResponse
    suspend fun updateMemberRole(teamId: TeamId, userId: String, request: UpdateMemberRoleRequest, session: UserSession)
    suspend fun removeMember(teamId: TeamId, userId: String, session: UserSession)
    suspend fun leaveTeam(teamId: TeamId, session: UserSession)

    suspend fun inviteUser(teamId: TeamId, request: CreateInviteRequest, session: UserSession): TeamInvite
    suspend fun listTeamInvites(teamId: TeamId, session: UserSession): InviteListResponse
    suspend fun cancelInvite(teamId: TeamId, inviteId: String, session: UserSession)

    suspend fun submitJoinRequest(teamId: TeamId, session: UserSession): JoinRequest
    suspend fun listJoinRequests(teamId: TeamId, session: UserSession): JoinRequestListResponse
    suspend fun approveJoinRequest(teamId: TeamId, requestId: String, session: UserSession)
    suspend fun rejectJoinRequest(teamId: TeamId, requestId: String, session: UserSession)

    suspend fun getMyInvites(session: UserSession): InviteListResponse
    suspend fun acceptInvite(inviteId: String, session: UserSession)
    suspend fun declineInvite(inviteId: String, session: UserSession)
}

class DefaultTeamService(
    private val teamRepository: TeamRepository,
    private val teamAccessService: TeamAccessService,
    private val auditService: AuditService,
    private val authService: com.github.mr3zee.auth.AuthService,
) : TeamService {

    override suspend fun createTeam(request: CreateTeamRequest, session: UserSession): TeamResponse {
        require(request.name.isNotBlank()) { "Team name must not be blank" }
        require(request.name.length <= 100) { "Team name must not exceed 100 characters" }
        require(request.description.length <= 2000) { "Team description must not exceed 2000 characters" }
        // Create the team and add the creator as TEAM_LEAD atomically in a single transaction
        val team = teamRepository.createTeamWithMember(request.name, request.description, session.userId, TeamRole.TEAM_LEAD)
        auditService.log(team.id, session, AuditAction.TEAM_CREATED, AuditTargetType.TEAM, team.id.value, "Created team '${team.name}'")
        return TeamResponse(team = team, memberCount = 1)
    }

    override suspend fun listTeams(offset: Int, limit: Int, search: String?): TeamListResponse {
        val (teams, totalCount) = teamRepository.findAll(offset, limit, search)
        val memberCounts = teamRepository.getMemberCounts(teams.map { it.id })
        val responses = teams.map { team ->
            TeamResponse(team = team, memberCount = memberCounts[team.id] ?: 0)
        }
        return TeamListResponse(
            teams = responses,
            pagination = PaginationInfo(totalCount = totalCount, offset = offset, limit = limit),
        )
    }

    override suspend fun getTeamDetail(teamId: TeamId, session: UserSession): TeamDetailResponse {
        val team = teamRepository.findById(teamId)
            ?: throw NotFoundException("Team not found")
        val isMember = session.role == UserRole.ADMIN || teamAccessService.isMember(teamId, session.userId)
        val members = if (isMember) teamRepository.findMembers(teamId) else emptyList()
        return TeamDetailResponse(team = team, members = members)
    }

    override suspend fun updateTeam(teamId: TeamId, request: UpdateTeamRequest, session: UserSession): TeamResponse {
        teamAccessService.checkTeamLead(teamId, session)
        val requestName = request.name
        if (requestName != null) {
            require(requestName.isNotBlank()) { "Team name must not be blank" }
            require(requestName.length <= 100) { "Team name must not exceed 100 characters" }
        }
        val requestDescription = request.description
        if (requestDescription != null) {
            require(requestDescription.length <= 2000) { "Team description must not exceed 2000 characters" }
        }
        val team = teamRepository.updateIfNameAvailable(teamId, request.name, request.description)
            ?: throw NotFoundException("Team not found")
        auditService.log(teamId, session, AuditAction.TEAM_UPDATED, AuditTargetType.TEAM, teamId.value, "Updated team '${team.name}'")
        return TeamResponse(team = team, memberCount = teamRepository.getMemberCount(teamId))
    }

    override suspend fun deleteTeam(teamId: TeamId, session: UserSession) {
        teamAccessService.checkTeamLead(teamId, session)
        teamRepository.deleteWithActiveReleaseCheck(teamId)
        auditService.log(teamId, session, AuditAction.TEAM_DELETED, AuditTargetType.TEAM, teamId.value, "Deleted team")
    }

    // Membership

    override suspend fun listMembers(teamId: TeamId, session: UserSession): TeamMemberListResponse {
        teamAccessService.checkMembership(teamId, session)
        return TeamMemberListResponse(members = teamRepository.findMembers(teamId))
    }

    override suspend fun updateMemberRole(teamId: TeamId, userId: String, request: UpdateMemberRoleRequest, session: UserSession) {
        teamAccessService.checkTeamLead(teamId, session)
        val membership = teamRepository.findMembership(teamId, userId)
            ?: throw NotFoundException("Member not found")
        // Protect last team lead
        if (membership.role == TeamRole.TEAM_LEAD && request.role != TeamRole.TEAM_LEAD) {
            val leadCount = teamRepository.countMembersWithRole(teamId, TeamRole.TEAM_LEAD)
            if (leadCount <= 1) {
                throw IllegalArgumentException("Cannot demote the last team lead")
            }
        }
        teamRepository.updateMemberRole(teamId, userId, request.role)
        auditService.log(teamId, session, AuditAction.MEMBER_ROLE_CHANGED, AuditTargetType.USER, userId, "Changed role to ${request.role}")
    }

    override suspend fun removeMember(teamId: TeamId, userId: String, session: UserSession) {
        teamAccessService.checkTeamLead(teamId, session)
        val membership = teamRepository.findMembership(teamId, userId)
            ?: throw NotFoundException("Member not found")
        if (membership.role == TeamRole.TEAM_LEAD) {
            val leadCount = teamRepository.countMembersWithRole(teamId, TeamRole.TEAM_LEAD)
            if (leadCount <= 1) {
                throw IllegalArgumentException("Cannot remove the last team lead")
            }
        }
        teamRepository.removeMember(teamId, userId)
        auditService.log(teamId, session, AuditAction.MEMBER_REMOVED, AuditTargetType.USER, userId, "Removed member from team")
    }

    override suspend fun leaveTeam(teamId: TeamId, session: UserSession) {
        val membership = teamRepository.findMembership(teamId, session.userId)
            ?: throw NotFoundException("Not a member of this team")
        if (membership.role == TeamRole.TEAM_LEAD) {
            val leadCount = teamRepository.countMembersWithRole(teamId, TeamRole.TEAM_LEAD)
            if (leadCount <= 1) {
                throw IllegalArgumentException("Cannot leave as the last team lead. Promote another member first.")
            }
        }
        teamRepository.removeMember(teamId, session.userId)
        auditService.log(teamId, session, AuditAction.MEMBER_LEFT, AuditTargetType.USER, session.userId, "Left team")
    }

    // Invites

    override suspend fun inviteUser(teamId: TeamId, request: CreateInviteRequest, session: UserSession): TeamInvite {
        teamAccessService.checkTeamLead(teamId, session)
        teamRepository.findById(teamId) ?: throw NotFoundException("Team not found")
        // Resolve username to user ID
        val user = authService.getUserByUsername(request.username)
            ?: throw NotFoundException("User '${request.username}' not found")
        val userId = user.id.value
        // Check not already a member
        if (teamAccessService.isMember(teamId, userId)) {
            throw IllegalArgumentException("User is already a member of this team")
        }
        // Check no existing pending invite
        val existing = teamRepository.findExistingPendingInvite(teamId, userId)
        if (existing != null) {
            throw IllegalArgumentException("User already has a pending invite")
        }
        val invite = teamRepository.createInvite(teamId, userId, session.userId)
        auditService.log(teamId, session, AuditAction.INVITE_SENT, AuditTargetType.USER, userId, "Invited user '${request.username}' to team")
        return invite
    }

    override suspend fun listTeamInvites(teamId: TeamId, session: UserSession): InviteListResponse {
        teamAccessService.checkTeamLead(teamId, session)
        return InviteListResponse(invites = teamRepository.findPendingInvitesByTeam(teamId))
    }

    override suspend fun cancelInvite(teamId: TeamId, inviteId: String, session: UserSession) {
        teamAccessService.checkTeamLead(teamId, session)
        val invite = teamRepository.findInviteById(inviteId)
            ?: throw NotFoundException("Invite not found")
        if (invite.teamId != teamId) throw ForbiddenException("Invite does not belong to this team")
        if (invite.status != InviteStatus.PENDING) throw IllegalArgumentException("Invite is not pending")
        teamRepository.updateInviteStatus(inviteId, InviteStatus.CANCELLED)
        auditService.log(teamId, session, AuditAction.INVITE_CANCELLED, AuditTargetType.USER, invite.invitedUserId.value, "Cancelled invite")
    }

    // Join Requests

    override suspend fun submitJoinRequest(teamId: TeamId, session: UserSession): JoinRequest {
        teamRepository.findById(teamId) ?: throw NotFoundException("Team not found")
        if (teamAccessService.isMember(teamId, session.userId)) {
            throw IllegalArgumentException("Already a member of this team")
        }
        val existing = teamRepository.findExistingPendingJoinRequest(teamId, session.userId)
        if (existing != null) {
            throw IllegalArgumentException("Already have a pending join request")
        }
        val joinRequest = teamRepository.createJoinRequest(teamId, session.userId)
        auditService.log(teamId, session, AuditAction.JOIN_REQUEST_SUBMITTED, AuditTargetType.USER, session.userId, "Submitted join request")
        return joinRequest
    }

    override suspend fun listJoinRequests(teamId: TeamId, session: UserSession): JoinRequestListResponse {
        teamAccessService.checkTeamLead(teamId, session)
        return JoinRequestListResponse(requests = teamRepository.findPendingJoinRequestsByTeam(teamId))
    }

    private suspend fun validatePendingJoinRequest(teamId: TeamId, requestId: String, session: UserSession): JoinRequest {
        teamAccessService.checkTeamLead(teamId, session)
        val request = teamRepository.findJoinRequestById(requestId)
            ?: throw NotFoundException("Join request not found")
        if (request.teamId != teamId) throw ForbiddenException("Request does not belong to this team")
        if (request.status != JoinRequestStatus.PENDING) throw IllegalArgumentException("Request is not pending")
        return request
    }

    override suspend fun approveJoinRequest(teamId: TeamId, requestId: String, session: UserSession) {
        val request = validatePendingJoinRequest(teamId, requestId, session)
        teamRepository.updateJoinRequestStatus(requestId, JoinRequestStatus.APPROVED, session.userId)
        teamRepository.addMember(teamId, request.userId.value, TeamRole.COLLABORATOR)
        auditService.log(teamId, session, AuditAction.JOIN_REQUEST_APPROVED, AuditTargetType.USER, request.userId.value, "Approved join request")
        auditService.log(teamId, session, AuditAction.MEMBER_JOINED, AuditTargetType.USER, request.userId.value, "Joined team via approved join request")
    }

    override suspend fun rejectJoinRequest(teamId: TeamId, requestId: String, session: UserSession) {
        val request = validatePendingJoinRequest(teamId, requestId, session)
        teamRepository.updateJoinRequestStatus(requestId, JoinRequestStatus.REJECTED, session.userId)
        auditService.log(teamId, session, AuditAction.JOIN_REQUEST_REJECTED, AuditTargetType.USER, request.userId.value, "Rejected join request")
    }

    // My Invites (user-facing)

    override suspend fun getMyInvites(session: UserSession): InviteListResponse {
        return InviteListResponse(invites = teamRepository.findPendingInvitesByUser(session.userId))
    }

    override suspend fun acceptInvite(inviteId: String, session: UserSession) {
        val invite = teamRepository.findInviteById(inviteId)
            ?: throw NotFoundException("Invite not found")
        if (invite.invitedUserId.value != session.userId) throw ForbiddenException("This invite is not for you")
        if (invite.status != InviteStatus.PENDING) throw IllegalArgumentException("Invite is not pending")
        if (teamAccessService.isMember(invite.teamId, session.userId)) {
            throw IllegalArgumentException("Already a member of this team")
        }
        teamRepository.updateInviteStatus(inviteId, InviteStatus.ACCEPTED)
        teamRepository.addMember(invite.teamId, session.userId, TeamRole.COLLABORATOR)
        auditService.log(invite.teamId, session, AuditAction.INVITE_ACCEPTED, AuditTargetType.USER, session.userId, "Accepted invite")
        auditService.log(invite.teamId, session, AuditAction.MEMBER_JOINED, AuditTargetType.USER, session.userId, "Joined team via accepted invite")
    }

    override suspend fun declineInvite(inviteId: String, session: UserSession) {
        val invite = teamRepository.findInviteById(inviteId)
            ?: throw NotFoundException("Invite not found")
        if (invite.invitedUserId.value != session.userId) throw ForbiddenException("This invite is not for you")
        if (invite.status != InviteStatus.PENDING) throw IllegalArgumentException("Invite is not pending")
        teamRepository.updateInviteStatus(inviteId, InviteStatus.DECLINED)
        auditService.log(invite.teamId, session, AuditAction.INVITE_DECLINED, AuditTargetType.USER, session.userId, "Declined invite")
    }
}
