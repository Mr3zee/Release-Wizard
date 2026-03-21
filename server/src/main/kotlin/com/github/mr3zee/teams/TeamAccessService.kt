package com.github.mr3zee.teams

import com.github.mr3zee.ForbiddenException
import com.github.mr3zee.auth.UserSession
import com.github.mr3zee.model.TeamId
import com.github.mr3zee.model.TeamMembership
import com.github.mr3zee.model.TeamRole
import com.github.mr3zee.model.UserRole
import org.slf4j.LoggerFactory

class TeamAccessService(private val teamRepository: TeamRepository) {
    private val log = LoggerFactory.getLogger(TeamAccessService::class.java)

    suspend fun checkMembership(teamId: TeamId, session: UserSession) {
        if (session.role == UserRole.ADMIN) return
        teamRepository.findMembership(teamId, session.userId)
            ?: run {
                log.warn("Access denied: user {} is not a member of team {}", session.userId, teamId.value)
                throw ForbiddenException("Not a member of this team")
            }
    }

    suspend fun checkTeamLead(teamId: TeamId, session: UserSession) {
        if (session.role == UserRole.ADMIN) return
        val membership = teamRepository.findMembership(teamId, session.userId)
        if (membership == null || membership.role != TeamRole.TEAM_LEAD) {
            log.warn("Access denied: user {} lacks team lead role for team {}", session.userId, teamId.value)
            throw ForbiddenException("Team lead access required")
        }
    }

    suspend fun getUserTeamIds(userId: String): List<TeamId> {
        return teamRepository.getUserTeams(userId).map { it.first.id }
    }

    suspend fun isMember(teamId: TeamId, userId: String): Boolean {
        return teamRepository.findMembership(teamId, userId) != null
    }

    // todo claude: unused
    suspend fun getMembership(teamId: TeamId, userId: String): TeamMembership? {
        return teamRepository.findMembership(teamId, userId)
    }
}
