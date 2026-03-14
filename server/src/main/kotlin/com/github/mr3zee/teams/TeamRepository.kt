package com.github.mr3zee.teams

import com.github.mr3zee.model.*
import com.github.mr3zee.persistence.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.sql.SQLIntegrityConstraintViolationException
import java.util.UUID
import kotlin.time.Clock

interface TeamRepository {
    suspend fun create(name: String, description: String): Team
    suspend fun findById(id: TeamId): Team?
    suspend fun findAll(offset: Int = 0, limit: Int = 20, search: String? = null): Pair<List<Team>, Long>
    suspend fun update(id: TeamId, name: String?, description: String?): Team?
    suspend fun delete(id: TeamId): Boolean
    suspend fun deleteWithActiveReleaseCheck(teamId: TeamId): Boolean
    suspend fun findByName(name: String): Team?
    suspend fun createIfNameAvailable(name: String, description: String): Team
    suspend fun updateIfNameAvailable(id: TeamId, name: String?, description: String?): Team?

    suspend fun addMember(teamId: TeamId, userId: String, role: TeamRole): Boolean
    suspend fun removeMember(teamId: TeamId, userId: String): Boolean
    suspend fun updateMemberRole(teamId: TeamId, userId: String, role: TeamRole): Boolean
    suspend fun findMembers(teamId: TeamId): List<TeamMembership>
    suspend fun findMembership(teamId: TeamId, userId: String): TeamMembership?
    suspend fun countMembersWithRole(teamId: TeamId, role: TeamRole): Long
    suspend fun getMemberCount(teamId: TeamId): Int
    suspend fun getMemberCounts(teamIds: List<TeamId>): Map<TeamId, Int>
    suspend fun getUserTeams(userId: String): List<Pair<Team, TeamRole>>

    suspend fun createInvite(teamId: TeamId, invitedUserId: String, invitedByUserId: String): TeamInvite
    suspend fun findInviteById(id: String): TeamInvite?
    suspend fun findPendingInvitesByTeam(teamId: TeamId): List<TeamInvite>
    suspend fun findPendingInvitesByUser(userId: String): List<TeamInvite>
    suspend fun updateInviteStatus(id: String, status: InviteStatus): Boolean
    suspend fun findExistingPendingInvite(teamId: TeamId, userId: String): TeamInvite?

    suspend fun createJoinRequest(teamId: TeamId, userId: String): JoinRequest
    suspend fun findJoinRequestById(id: String): JoinRequest?
    suspend fun findPendingJoinRequestsByTeam(teamId: TeamId): List<JoinRequest>
    suspend fun updateJoinRequestStatus(id: String, status: JoinRequestStatus, reviewedByUserId: String): Boolean
    suspend fun findExistingPendingJoinRequest(teamId: TeamId, userId: String): JoinRequest?
}

class ExposedTeamRepository(private val db: Database) : TeamRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction(db) { block() } }

    private fun ResultRow.toTeam(): Team = Team(
        id = TeamId(this[TeamTable.id].value.toString()),
        name = this[TeamTable.name],
        description = this[TeamTable.description],
        createdAt = this[TeamTable.createdAt].toEpochMilliseconds(),
    )

    /**
     * Batch-fetch usernames for a collection of user ID strings.
     * Returns a map from user ID string to username.
     */
    private fun batchLookupUsernames(userIds: Collection<String>): Map<String, String> {
        if (userIds.isEmpty()) return emptyMap()
        val uuids = userIds.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
        if (uuids.isEmpty()) return emptyMap()
        return UserTable.selectAll()
            .where { UserTable.id inList uuids }
            .associate { it[UserTable.id].value.toString() to it[UserTable.username] }
    }

    /**
     * Batch-fetch team names for a collection of team ID strings.
     * Returns a map from team ID string to team name.
     */
    private fun batchLookupTeamNames(teamIds: Collection<String>): Map<String, String> {
        if (teamIds.isEmpty()) return emptyMap()
        val uuids = teamIds.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
        if (uuids.isEmpty()) return emptyMap()
        return TeamTable.selectAll()
            .where { TeamTable.id inList uuids }
            .associate { it[TeamTable.id].value.toString() to it[TeamTable.name] }
    }

    override suspend fun create(name: String, description: String): Team = dbQuery {
        val now = Clock.System.now()
        val id = UUID.randomUUID()
        TeamTable.insert {
            it[TeamTable.id] = id
            it[TeamTable.name] = name
            it[TeamTable.description] = description
            it[TeamTable.createdAt] = now
        }
        Team(id = TeamId(id.toString()), name = name, description = description, createdAt = now.toEpochMilliseconds())
    }

    override suspend fun createIfNameAvailable(name: String, description: String): Team = dbQuery {
        try {
            val now = Clock.System.now()
            val id = UUID.randomUUID()
            TeamTable.insert {
                it[TeamTable.id] = id
                it[TeamTable.name] = name
                it[TeamTable.description] = description
                it[TeamTable.createdAt] = now
            }
            Team(id = TeamId(id.toString()), name = name, description = description, createdAt = now.toEpochMilliseconds())
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            val isSqlConstraint = e is SQLIntegrityConstraintViolationException ||
                e.cause is SQLIntegrityConstraintViolationException ||
                e.message?.contains("unique constraint", ignoreCase = true) == true ||
                e.message?.contains("duplicate key", ignoreCase = true) == true ||
                e.cause?.message?.contains("unique constraint", ignoreCase = true) == true ||
                e.cause?.message?.contains("duplicate key", ignoreCase = true) == true
            if (isSqlConstraint) {
                throw IllegalArgumentException("Team name already taken")
            }
            throw e
        }
    }

    override suspend fun updateIfNameAvailable(id: TeamId, name: String?, description: String?): Team? = dbQuery {
        try {
            val uuid = UUID.fromString(id.value)
            val updated = TeamTable.update({ TeamTable.id eq uuid }) { stmt ->
                name?.let { stmt[TeamTable.name] = it }
                description?.let { stmt[TeamTable.description] = it }
            }
            if (updated > 0) TeamTable.selectAll().where { TeamTable.id eq uuid }.single().toTeam() else null
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            val isSqlConstraint = e is SQLIntegrityConstraintViolationException ||
                e.cause is SQLIntegrityConstraintViolationException ||
                e.message?.contains("unique constraint", ignoreCase = true) == true ||
                e.message?.contains("duplicate key", ignoreCase = true) == true ||
                e.cause?.message?.contains("unique constraint", ignoreCase = true) == true ||
                e.cause?.message?.contains("duplicate key", ignoreCase = true) == true
            if (isSqlConstraint) {
                throw IllegalArgumentException("Team name already taken")
            }
            throw e
        }
    }

    override suspend fun findById(id: TeamId): Team? = dbQuery {
        TeamTable.selectAll()
            .where { TeamTable.id eq UUID.fromString(id.value) }
            .singleOrNull()?.toTeam()
    }

    override suspend fun findByName(name: String): Team? = dbQuery {
        TeamTable.selectAll()
            .where { TeamTable.name eq name }
            .singleOrNull()?.toTeam()
    }

    override suspend fun findAll(offset: Int, limit: Int, search: String?): Pair<List<Team>, Long> = dbQuery {
        val conditions = mutableListOf<Op<Boolean>>()
        if (!search.isNullOrBlank()) {
            conditions.add(TeamTable.name.lowerCase() like likeContains(search))
        }
        val countQuery = TeamTable.selectAll()
        if (conditions.isNotEmpty()) countQuery.where { conditions.reduce { acc, op -> acc and op } }
        val totalCount = countQuery.count()

        val dataQuery = TeamTable.selectAll()
        if (conditions.isNotEmpty()) dataQuery.where { conditions.reduce { acc, op -> acc and op } }
        val teams = dataQuery.orderBy(TeamTable.name, SortOrder.ASC)
            .limit(limit).offset(safeOffset(offset))
            .map { it.toTeam() }
        teams to totalCount
    }

    override suspend fun update(id: TeamId, name: String?, description: String?): Team? = dbQuery {
        val uuid = UUID.fromString(id.value)
        val updated = TeamTable.update({ TeamTable.id eq uuid }) { stmt ->
            name?.let { stmt[TeamTable.name] = it }
            description?.let { stmt[TeamTable.description] = it }
        }
        if (updated > 0) TeamTable.selectAll().where { TeamTable.id eq uuid }.single().toTeam() else null
    }

    override suspend fun delete(id: TeamId): Boolean = dbQuery {
        TeamMembershipTable.deleteWhere { TeamMembershipTable.teamId eq id.value }
        TeamInviteTable.deleteWhere { TeamInviteTable.teamId eq id.value }
        JoinRequestTable.deleteWhere { JoinRequestTable.teamId eq id.value }
        TeamTable.deleteWhere { TeamTable.id eq UUID.fromString(id.value) } > 0
    }

    override suspend fun deleteWithActiveReleaseCheck(teamId: TeamId): Boolean = dbQuery {
        val hasActiveReleases = ReleaseTable.selectAll()
            .where {
                (ReleaseTable.teamId eq teamId.value) and
                    (ReleaseTable.status inList listOf("PENDING", "RUNNING"))
            }
            .count() > 0
        if (hasActiveReleases) {
            throw IllegalArgumentException("Cannot delete team with active releases")
        }
        TeamMembershipTable.deleteWhere { TeamMembershipTable.teamId eq teamId.value }
        TeamInviteTable.deleteWhere { TeamInviteTable.teamId eq teamId.value }
        JoinRequestTable.deleteWhere { JoinRequestTable.teamId eq teamId.value }
        TeamTable.deleteWhere { TeamTable.id eq UUID.fromString(teamId.value) } > 0
    }

    // Membership

    override suspend fun addMember(teamId: TeamId, userId: String, role: TeamRole): Boolean = dbQuery {
        TeamMembershipTable.insert {
            it[TeamMembershipTable.teamId] = teamId.value
            it[TeamMembershipTable.userId] = userId
            it[TeamMembershipTable.role] = role.name
            it[TeamMembershipTable.joinedAt] = Clock.System.now()
        }
        true
    }

    override suspend fun removeMember(teamId: TeamId, userId: String): Boolean = dbQuery {
        TeamMembershipTable.deleteWhere {
            (TeamMembershipTable.teamId eq teamId.value) and (TeamMembershipTable.userId eq userId)
        } > 0
    }

    override suspend fun updateMemberRole(teamId: TeamId, userId: String, role: TeamRole): Boolean = dbQuery {
        TeamMembershipTable.update({
            (TeamMembershipTable.teamId eq teamId.value) and (TeamMembershipTable.userId eq userId)
        }) { it[TeamMembershipTable.role] = role.name } > 0
    }

    override suspend fun findMembers(teamId: TeamId): List<TeamMembership> = dbQuery {
        val rows = TeamMembershipTable.selectAll()
            .where { TeamMembershipTable.teamId eq teamId.value }
            .toList()
        val userIds = rows.map { it[TeamMembershipTable.userId] }.toSet()
        val usernameMap = batchLookupUsernames(userIds)
        rows.map { row ->
            TeamMembership(
                teamId = TeamId(row[TeamMembershipTable.teamId]),
                userId = UserId(row[TeamMembershipTable.userId]),
                username = usernameMap[row[TeamMembershipTable.userId]] ?: "",
                role = TeamRole.valueOf(row[TeamMembershipTable.role]),
                joinedAt = row[TeamMembershipTable.joinedAt].toEpochMilliseconds(),
            )
        }
    }

    override suspend fun findMembership(teamId: TeamId, userId: String): TeamMembership? = dbQuery {
        TeamMembershipTable.selectAll()
            .where { (TeamMembershipTable.teamId eq teamId.value) and (TeamMembershipTable.userId eq userId) }
            .singleOrNull()?.let { row ->
                val usernameMap = batchLookupUsernames(setOf(row[TeamMembershipTable.userId]))
                TeamMembership(
                    teamId = TeamId(row[TeamMembershipTable.teamId]),
                    userId = UserId(row[TeamMembershipTable.userId]),
                    username = usernameMap[row[TeamMembershipTable.userId]] ?: "",
                    role = TeamRole.valueOf(row[TeamMembershipTable.role]),
                    joinedAt = row[TeamMembershipTable.joinedAt].toEpochMilliseconds(),
                )
            }
    }

    override suspend fun countMembersWithRole(teamId: TeamId, role: TeamRole): Long = dbQuery {
        TeamMembershipTable.selectAll()
            .where { (TeamMembershipTable.teamId eq teamId.value) and (TeamMembershipTable.role eq role.name) }
            .count()
    }

    override suspend fun getMemberCount(teamId: TeamId): Int = dbQuery {
        TeamMembershipTable.selectAll()
            .where { TeamMembershipTable.teamId eq teamId.value }
            .count().toInt()
    }

    override suspend fun getMemberCounts(teamIds: List<TeamId>): Map<TeamId, Int> = dbQuery {
        if (teamIds.isEmpty()) return@dbQuery emptyMap()
        val teamIdStrings = teamIds.map { it.value }
        TeamMembershipTable
            .select(TeamMembershipTable.teamId, TeamMembershipTable.teamId.count())
            .where { TeamMembershipTable.teamId inList teamIdStrings }
            .groupBy(TeamMembershipTable.teamId)
            .associate { row ->
                TeamId(row[TeamMembershipTable.teamId]) to row[TeamMembershipTable.teamId.count()].toInt()
            }
    }

    override suspend fun getUserTeams(userId: String): List<Pair<Team, TeamRole>> = dbQuery {
        val rows = TeamMembershipTable.selectAll()
            .where { TeamMembershipTable.userId eq userId }
            .toList()
        if (rows.isEmpty()) return@dbQuery emptyList()
        val teamIdStrings = rows.map { it[TeamMembershipTable.teamId] }.toSet()
        val teamUuids = teamIdStrings.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
        val teamMap = TeamTable.selectAll()
            .where { TeamTable.id inList teamUuids }
            .associate { it[TeamTable.id].value.toString() to it.toTeam() }
        rows.mapNotNull { row ->
            val teamId = row[TeamMembershipTable.teamId]
            val team = teamMap[teamId] ?: return@mapNotNull null
            team to TeamRole.valueOf(row[TeamMembershipTable.role])
        }
    }

    // Invites

    override suspend fun createInvite(teamId: TeamId, invitedUserId: String, invitedByUserId: String): TeamInvite = dbQuery {
        val now = Clock.System.now()
        val id = UUID.randomUUID()
        TeamInviteTable.insert {
            it[TeamInviteTable.id] = id
            it[TeamInviteTable.teamId] = teamId.value
            it[TeamInviteTable.invitedUserId] = invitedUserId
            it[TeamInviteTable.invitedByUserId] = invitedByUserId
            it[TeamInviteTable.status] = InviteStatus.PENDING.name
            it[TeamInviteTable.createdAt] = now
        }
        val usernameMap = batchLookupUsernames(setOf(invitedUserId, invitedByUserId))
        val teamNameMap = batchLookupTeamNames(setOf(teamId.value))
        TeamInvite(
            id = id.toString(),
            teamId = teamId,
            teamName = teamNameMap[teamId.value] ?: "",
            invitedUserId = UserId(invitedUserId),
            invitedUsername = usernameMap[invitedUserId] ?: "",
            invitedByUserId = UserId(invitedByUserId),
            invitedByUsername = usernameMap[invitedByUserId] ?: "",
            status = InviteStatus.PENDING,
            createdAt = now.toEpochMilliseconds(),
        )
    }

    override suspend fun findInviteById(id: String): TeamInvite? = dbQuery {
        val row = TeamInviteTable.selectAll()
            .where { TeamInviteTable.id eq UUID.fromString(id) }
            .singleOrNull() ?: return@dbQuery null
        mapInviteRows(listOf(row)).firstOrNull()
    }

    override suspend fun findPendingInvitesByTeam(teamId: TeamId): List<TeamInvite> = dbQuery {
        val rows = TeamInviteTable.selectAll()
            .where { (TeamInviteTable.teamId eq teamId.value) and (TeamInviteTable.status eq InviteStatus.PENDING.name) }
            .toList()
        mapInviteRows(rows)
    }

    override suspend fun findPendingInvitesByUser(userId: String): List<TeamInvite> = dbQuery {
        val rows = TeamInviteTable.selectAll()
            .where { (TeamInviteTable.invitedUserId eq userId) and (TeamInviteTable.status eq InviteStatus.PENDING.name) }
            .toList()
        mapInviteRows(rows)
    }

    override suspend fun updateInviteStatus(id: String, status: InviteStatus): Boolean = dbQuery {
        TeamInviteTable.update({ TeamInviteTable.id eq UUID.fromString(id) }) {
            it[TeamInviteTable.status] = status.name
        } > 0
    }

    override suspend fun findExistingPendingInvite(teamId: TeamId, userId: String): TeamInvite? = dbQuery {
        val row = TeamInviteTable.selectAll()
            .where {
                (TeamInviteTable.teamId eq teamId.value) and
                    (TeamInviteTable.invitedUserId eq userId) and
                    (TeamInviteTable.status eq InviteStatus.PENDING.name)
            }
            .singleOrNull() ?: return@dbQuery null
        mapInviteRows(listOf(row)).firstOrNull()
    }

    /**
     * Batch-map invite rows: collects all user IDs and team IDs, fetches them in bulk,
     * then maps each row to a TeamInvite.
     */
    private fun mapInviteRows(rows: List<ResultRow>): List<TeamInvite> {
        if (rows.isEmpty()) return emptyList()
        val allUserIds = mutableSetOf<String>()
        val allTeamIds = mutableSetOf<String>()
        for (row in rows) {
            allTeamIds.add(row[TeamInviteTable.teamId])
            allUserIds.add(row[TeamInviteTable.invitedUserId])
            allUserIds.add(row[TeamInviteTable.invitedByUserId])
        }
        val usernameMap = batchLookupUsernames(allUserIds)
        val teamNameMap = batchLookupTeamNames(allTeamIds)
        return rows.map { row ->
            TeamInvite(
                id = row[TeamInviteTable.id].value.toString(),
                teamId = TeamId(row[TeamInviteTable.teamId]),
                teamName = teamNameMap[row[TeamInviteTable.teamId]] ?: "",
                invitedUserId = UserId(row[TeamInviteTable.invitedUserId]),
                invitedUsername = usernameMap[row[TeamInviteTable.invitedUserId]] ?: "",
                invitedByUserId = UserId(row[TeamInviteTable.invitedByUserId]),
                invitedByUsername = usernameMap[row[TeamInviteTable.invitedByUserId]] ?: "",
                status = InviteStatus.valueOf(row[TeamInviteTable.status]),
                createdAt = row[TeamInviteTable.createdAt].toEpochMilliseconds(),
            )
        }
    }

    // Join Requests

    override suspend fun createJoinRequest(teamId: TeamId, userId: String): JoinRequest = dbQuery {
        val now = Clock.System.now()
        val id = UUID.randomUUID()
        JoinRequestTable.insert {
            it[JoinRequestTable.id] = id
            it[JoinRequestTable.teamId] = teamId.value
            it[JoinRequestTable.userId] = userId
            it[JoinRequestTable.status] = JoinRequestStatus.PENDING.name
            it[JoinRequestTable.createdAt] = now
        }
        val usernameMap = batchLookupUsernames(setOf(userId))
        val teamNameMap = batchLookupTeamNames(setOf(teamId.value))
        JoinRequest(
            id = id.toString(),
            teamId = teamId,
            teamName = teamNameMap[teamId.value] ?: "",
            userId = UserId(userId),
            username = usernameMap[userId] ?: "",
            status = JoinRequestStatus.PENDING,
            createdAt = now.toEpochMilliseconds(),
        )
    }

    override suspend fun findJoinRequestById(id: String): JoinRequest? = dbQuery {
        val row = JoinRequestTable.selectAll()
            .where { JoinRequestTable.id eq UUID.fromString(id) }
            .singleOrNull() ?: return@dbQuery null
        mapJoinRequestRows(listOf(row)).firstOrNull()
    }

    override suspend fun findPendingJoinRequestsByTeam(teamId: TeamId): List<JoinRequest> = dbQuery {
        val rows = JoinRequestTable.selectAll()
            .where { (JoinRequestTable.teamId eq teamId.value) and (JoinRequestTable.status eq JoinRequestStatus.PENDING.name) }
            .toList()
        mapJoinRequestRows(rows)
    }

    override suspend fun updateJoinRequestStatus(id: String, status: JoinRequestStatus, reviewedByUserId: String): Boolean = dbQuery {
        JoinRequestTable.update({ JoinRequestTable.id eq UUID.fromString(id) }) {
            it[JoinRequestTable.status] = status.name
            it[JoinRequestTable.reviewedByUserId] = reviewedByUserId
            it[JoinRequestTable.reviewedAt] = Clock.System.now()
        } > 0
    }

    override suspend fun findExistingPendingJoinRequest(teamId: TeamId, userId: String): JoinRequest? = dbQuery {
        val row = JoinRequestTable.selectAll()
            .where {
                (JoinRequestTable.teamId eq teamId.value) and
                    (JoinRequestTable.userId eq userId) and
                    (JoinRequestTable.status eq JoinRequestStatus.PENDING.name)
            }
            .singleOrNull() ?: return@dbQuery null
        mapJoinRequestRows(listOf(row)).firstOrNull()
    }

    /**
     * Batch-map join request rows: collects all user IDs and team IDs, fetches them in bulk,
     * then maps each row to a JoinRequest.
     */
    private fun mapJoinRequestRows(rows: List<ResultRow>): List<JoinRequest> {
        if (rows.isEmpty()) return emptyList()
        val allUserIds = mutableSetOf<String>()
        val allTeamIds = mutableSetOf<String>()
        for (row in rows) {
            allTeamIds.add(row[JoinRequestTable.teamId])
            allUserIds.add(row[JoinRequestTable.userId])
            row[JoinRequestTable.reviewedByUserId]?.let { allUserIds.add(it) }
        }
        val usernameMap = batchLookupUsernames(allUserIds)
        val teamNameMap = batchLookupTeamNames(allTeamIds)
        return rows.map { row ->
            val reviewerId = row[JoinRequestTable.reviewedByUserId]
            JoinRequest(
                id = row[JoinRequestTable.id].value.toString(),
                teamId = TeamId(row[JoinRequestTable.teamId]),
                teamName = teamNameMap[row[JoinRequestTable.teamId]] ?: "",
                userId = UserId(row[JoinRequestTable.userId]),
                username = usernameMap[row[JoinRequestTable.userId]] ?: "",
                status = JoinRequestStatus.valueOf(row[JoinRequestTable.status]),
                reviewedByUserId = reviewerId?.let { UserId(it) },
                reviewedByUsername = reviewerId?.let { usernameMap[it] ?: "" },
                createdAt = row[JoinRequestTable.createdAt].toEpochMilliseconds(),
                reviewedAt = row[JoinRequestTable.reviewedAt]?.toEpochMilliseconds(),
            )
        }
    }
}
