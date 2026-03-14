package com.github.mr3zee.teams

import com.github.mr3zee.ForbiddenException
import com.github.mr3zee.auth.UserSession
import com.github.mr3zee.model.TeamId
import com.github.mr3zee.model.TeamMembership
import com.github.mr3zee.model.TeamRole
import com.github.mr3zee.model.UserRole

class TeamAccessService(private val teamRepository: TeamRepository) {

    suspend fun checkMembership(teamId: TeamId, session: UserSession) {
        if (session.role == UserRole.ADMIN) return
        val membership = teamRepository.findMembership(teamId, session.userId)
        if (membership == null) {
            throw ForbiddenException("Not a member of this team")
        }
    }

    suspend fun checkTeamLead(teamId: TeamId, session: UserSession) {
        if (session.role == UserRole.ADMIN) return
        val membership = teamRepository.findMembership(teamId, session.userId)
        if (membership == null || membership.role != TeamRole.TEAM_LEAD) {
            throw ForbiddenException("Team lead access required")
        }
    }

    suspend fun getTeamRole(teamId: TeamId, userId: String): TeamRole? {
        return teamRepository.findMembership(teamId, userId)?.role
    }

    suspend fun getUserTeamIds(userId: String): List<TeamId> {
        return teamRepository.getUserTeams(userId).map { it.first.id }
    }

    suspend fun isMember(teamId: TeamId, userId: String): Boolean {
        return teamRepository.findMembership(teamId, userId) != null
    }

    suspend fun getMembership(teamId: TeamId, userId: String): TeamMembership? {
        return teamRepository.findMembership(teamId, userId)
    }
}
