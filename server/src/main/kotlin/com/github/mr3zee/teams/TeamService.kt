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
        val name = sanitizeName(request.name)
        require(name.isNotBlank()) { "Team name must not be blank" }
        require(name.length <= 100) { "Team name must not exceed 100 characters" }
        require(request.description.length <= 2000) { "Team description must not exceed 2000 characters" }
        // Create the team and add the creator as TEAM_LEAD atomically in a single transaction
        val team = teamRepository.createTeamWithMember(name, request.description, session.userId, TeamRole.TEAM_LEAD)
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
        val isAdmin = session.role == UserRole.ADMIN
        val isActualMember = teamAccessService.isMember(teamId, session.userId)
        val canAccess = isAdmin || isActualMember
        // TEAM-M5: Audit when admin reads team data they're not a member of
        if (isAdmin && !isActualMember) {
            auditService.log(teamId, session, AuditAction.ADMIN_ACCESS, AuditTargetType.TEAM, teamId.value, "Admin viewed team details")
        }
        val members = if (canAccess) teamRepository.findMembers(teamId) else emptyList()
        return TeamDetailResponse(team = team, members = members)
    }

    override suspend fun updateTeam(teamId: TeamId, request: UpdateTeamRequest, session: UserSession): TeamResponse {
        teamAccessService.checkTeamLead(teamId, session)
        // TEAM-M3: Reject both-null update — at least one field must be provided
        require(request.name != null || request.description != null) {
            "At least one field (name or description) must be provided for update"
        }
        val requestName = request.name?.let { sanitizeName(it) }
        if (requestName != null) {
            require(requestName.isNotBlank()) { "Team name must not be blank" }
            require(requestName.length <= 100) { "Team name must not exceed 100 characters" }
        }
        val requestDescription = request.description
        if (requestDescription != null) {
            require(requestDescription.length <= 2000) { "Team description must not exceed 2000 characters" }
        }
        val team = teamRepository.updateIfNameAvailable(teamId, requestName, request.description)
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
        require(userId != session.userId) { "Cannot change your own role" }
        teamAccessService.checkTeamLead(teamId, session)
        // TEAM-C1: Atomic role update with last-lead protection in a single transaction
        teamRepository.updateMemberRoleAtomic(teamId, userId, request.role)
        auditService.log(teamId, session, AuditAction.MEMBER_ROLE_CHANGED, AuditTargetType.USER, userId, "Changed role to ${request.role}")
    }

    override suspend fun removeMember(teamId: TeamId, userId: String, session: UserSession) {
        require(userId != session.userId) { "Use the leave endpoint to leave a team" }
        teamAccessService.checkTeamLead(teamId, session)
        // TEAM-C1: Atomic removal with last-lead protection in a single transaction
        teamRepository.removeMemberAtomic(teamId, userId)
        auditService.log(teamId, session, AuditAction.MEMBER_REMOVED, AuditTargetType.USER, userId, "Removed member from team")
    }

    override suspend fun leaveTeam(teamId: TeamId, session: UserSession) {
        // TEAM-C1: Atomic removal with last-lead protection in a single transaction
        teamRepository.removeMemberAtomic(teamId, session.userId)
        auditService.log(teamId, session, AuditAction.MEMBER_LEFT, AuditTargetType.USER, session.userId, "Left team")
    }

    // Invites

    override suspend fun inviteUser(teamId: TeamId, request: CreateInviteRequest, session: UserSession): TeamInvite {
        teamAccessService.checkTeamLead(teamId, session)
        teamRepository.findById(teamId) ?: throw NotFoundException("Team not found")
        // TEAM-L2: Use generic error message to prevent username enumeration via invite flow
        val user = authService.getUserByUsername(request.username)
            ?: throw NotFoundException("Unable to invite user")
        val userId = user.id.value
        // TEAM-L2: Use generic message to prevent username enumeration
        if (teamAccessService.isMember(teamId, userId)) {
            throw IllegalArgumentException("Unable to invite user")
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
        teamAccessService.checkTeamLead(teamId, session)
        // TEAM-C2: Atomic approve + add member in a single transaction to prevent duplicate memberships
        val requestUserId = teamRepository.approveJoinRequestAtomic(teamId, requestId, session.userId, TeamRole.COLLABORATOR)
        auditService.log(teamId, session, AuditAction.JOIN_REQUEST_APPROVED, AuditTargetType.USER, requestUserId, "Approved join request")
        auditService.log(teamId, session, AuditAction.MEMBER_JOINED, AuditTargetType.USER, requestUserId, "Joined team via approved join request")
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
        // TEAM-C2: Atomic invite accept + add member in a single transaction to prevent duplicate memberships
        val teamId = teamRepository.acceptInviteAtomic(inviteId, session.userId, TeamRole.COLLABORATOR)
        // TEAM-L3: Best-effort cancel of pending join request now that user joined via invite.
        // Runs in a separate transaction — failure is logged but doesn't block the invite acceptance.
        try {
            teamRepository.cancelPendingJoinRequest(teamId, session.userId, reviewedByUserId = session.userId)
        } catch (e: Exception) {
            val log = org.slf4j.LoggerFactory.getLogger(DefaultTeamService::class.java)
            log.warn("Failed to cancel pending join request for user {} in team {}: {}", session.userId, teamId.value, e.message)
        }
        auditService.log(teamId, session, AuditAction.INVITE_ACCEPTED, AuditTargetType.USER, session.userId, "Accepted invite")
        auditService.log(teamId, session, AuditAction.MEMBER_JOINED, AuditTargetType.USER, session.userId, "Joined team via accepted invite")
    }

    private fun sanitizeName(name: String): String {
        val trimmed = name.trim()
        require(trimmed.none { it.isISOControl() || it.category == CharCategory.FORMAT }) { "Name contains invalid characters" }
        return trimmed
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
