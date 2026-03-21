package com.github.mr3zee.teams

import com.github.mr3zee.ForbiddenException
import com.github.mr3zee.NotFoundException
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

interface TeamRepository {
    suspend fun create(name: String, description: String): Team
    suspend fun findById(id: TeamId): Team?
    suspend fun findAll(offset: Int = 0, limit: Int = 20, search: String? = null): Pair<List<Team>, Long>
    suspend fun update(id: TeamId, name: String?, description: String?): Team?
    suspend fun delete(id: TeamId): Boolean
    suspend fun deleteWithActiveReleaseCheck(teamId: TeamId): Boolean
    suspend fun findByName(name: String): Team?
    suspend fun createIfNameAvailable(name: String, description: String): Team
    suspend fun createTeamWithMember(name: String, description: String, userId: String, role: TeamRole): Team
    suspend fun updateIfNameAvailable(id: TeamId, name: String?, description: String?): Team?

    suspend fun addMember(teamId: TeamId, userId: String, role: TeamRole): Boolean
    suspend fun removeMember(teamId: TeamId, userId: String): Boolean
    suspend fun updateMemberRole(teamId: TeamId, userId: String, role: TeamRole): Boolean

    /**
     * TEAM-C1: Atomically update a member's role with last-lead protection.
     * Uses SELECT FOR UPDATE to prevent TOCTOU race on concurrent demotions.
     * @throws IllegalArgumentException if demoting the last team lead
     * @throws com.github.mr3zee.NotFoundException if member not found
     */
    suspend fun updateMemberRoleAtomic(teamId: TeamId, userId: String, newRole: TeamRole): Boolean

    /**
     * TEAM-C1: Atomically remove a member with last-lead protection.
     * Uses SELECT FOR UPDATE to prevent TOCTOU race on concurrent removals.
     * @throws IllegalArgumentException if removing the last team lead
     * @throws com.github.mr3zee.NotFoundException if member not found
     */
    suspend fun removeMemberAtomic(teamId: TeamId, userId: String): Boolean

    /**
     * TEAM-C2: Atomically accept an invite: validate, update status, and add member in one transaction.
     * Catches duplicate membership as a conflict.
     * @return the team ID of the accepted invite
     * @throws com.github.mr3zee.NotFoundException if invite not found
     * @throws com.github.mr3zee.ForbiddenException if invite is not for the given user
     * @throws IllegalArgumentException if invite is not pending or user is already a member
     */
    suspend fun acceptInviteAtomic(inviteId: String, userId: String, role: TeamRole): TeamId

    /**
     * TEAM-C2: Atomically approve a join request: validate, update status, and add member in one transaction.
     * Catches duplicate membership as a conflict.
     * @return pair of (teamId, requestingUserId)
     * @throws com.github.mr3zee.NotFoundException if join request not found
     * @throws com.github.mr3zee.ForbiddenException if request doesn't belong to the team
     * @throws IllegalArgumentException if request is not pending or user is already a member
     */
    suspend fun approveJoinRequestAtomic(teamId: TeamId, requestId: String, reviewedByUserId: String, role: TeamRole): String
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

    /** TEAM-L3: Cancel any pending join request for a user in a team (e.g., after invite accept). */
    suspend fun cancelPendingJoinRequest(teamId: TeamId, userId: String, reviewedByUserId: String? = null)
}

class ExposedTeamRepository(private val db: Database) : TeamRepository {

    companion object {
        /** TEAM-H5: Invite expiry duration (7 days) */
        val INVITE_EXPIRY_DURATION: Duration = 7.days
    }

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

    /**
     * Detects a unique constraint violation from any SQL exception.
     */
    private fun isUniqueConstraintViolation(e: Exception): Boolean {
        return e is SQLIntegrityConstraintViolationException ||
            e.cause is SQLIntegrityConstraintViolationException ||
            e.message?.contains("unique constraint", ignoreCase = true) == true ||
            e.message?.contains("duplicate key", ignoreCase = true) == true ||
            e.cause?.message?.contains("unique constraint", ignoreCase = true) == true ||
            e.cause?.message?.contains("duplicate key", ignoreCase = true) == true
    }

    private fun insertTeam(name: String, description: String): Team {
        val now = Clock.System.now()
        val id = UUID.randomUUID()
        TeamTable.insert {
            it[TeamTable.id] = id
            it[TeamTable.name] = name
            it[TeamTable.description] = description
            it[TeamTable.createdAt] = now
        }
        return Team(id = TeamId(id.toString()), name = name, description = description, createdAt = now.toEpochMilliseconds())
    }

    private inline fun <T> catchUniqueConstraint(block: () -> T): T {
        return try {
            block()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            if (isUniqueConstraintViolation(e)) {
                throw IllegalArgumentException("Team name already taken")
            }
            throw e
        }
    }

    override suspend fun create(name: String, description: String): Team = dbQuery {
        insertTeam(name, description)
    }

    override suspend fun createIfNameAvailable(name: String, description: String): Team = dbQuery {
        catchUniqueConstraint { insertTeam(name, description) }
    }

    override suspend fun createTeamWithMember(name: String, description: String, userId: String, role: TeamRole): Team = dbQuery {
        catchUniqueConstraint {
            val team = insertTeam(name, description)
            TeamMembershipTable.insert {
                it[TeamMembershipTable.teamId] = UUID.fromString(team.id.value)
                it[TeamMembershipTable.userId] = UUID.fromString(userId)
                it[TeamMembershipTable.role] = role
                it[TeamMembershipTable.joinedAt] = Clock.System.now()
            }
            team
        }
    }

    override suspend fun updateIfNameAvailable(id: TeamId, name: String?, description: String?): Team? = dbQuery {
        catchUniqueConstraint { performTeamUpdate(id, name, description) }
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

    private fun performTeamUpdate(id: TeamId, name: String?, description: String?): Team? {
        val uuid = UUID.fromString(id.value)
        val updated = TeamTable.update({ TeamTable.id eq uuid }) { stmt ->
            name?.let { stmt[TeamTable.name] = it }
            description?.let { stmt[TeamTable.description] = it }
        }
        return if (updated > 0) TeamTable.selectAll().where { TeamTable.id eq uuid }.single().toTeam() else null
    }

    override suspend fun update(id: TeamId, name: String?, description: String?): Team? = dbQuery {
        performTeamUpdate(id, name, description)
    }

    private fun deleteTeamAndDependencies(teamUuid: UUID): Boolean {
        TeamMembershipTable.deleteWhere { TeamMembershipTable.teamId eq teamUuid }
        TeamInviteTable.deleteWhere { TeamInviteTable.teamId eq teamUuid }
        JoinRequestTable.deleteWhere { JoinRequestTable.teamId eq teamUuid }
        return TeamTable.deleteWhere { TeamTable.id eq teamUuid } > 0
    }

    override suspend fun delete(id: TeamId): Boolean = dbQuery {
        deleteTeamAndDependencies(UUID.fromString(id.value))
    }

    override suspend fun deleteWithActiveReleaseCheck(teamId: TeamId): Boolean = dbQuery {
        val teamUuid = UUID.fromString(teamId.value)
        val hasActiveReleases = ReleaseTable.selectAll()
            .where {
                (ReleaseTable.teamId eq teamUuid) and
                    (ReleaseTable.status inList listOf(ReleaseStatus.PENDING, ReleaseStatus.RUNNING))
            }
            .count() > 0
        if (hasActiveReleases) {
            throw IllegalArgumentException("Cannot delete team with active releases")
        }
        deleteTeamAndDependencies(teamUuid)
    }

    // Membership

    override suspend fun addMember(teamId: TeamId, userId: String, role: TeamRole): Boolean = dbQuery {
        TeamMembershipTable.insert {
            it[TeamMembershipTable.teamId] = UUID.fromString(teamId.value)
            it[TeamMembershipTable.userId] = UUID.fromString(userId)
            it[TeamMembershipTable.role] = role
            it[TeamMembershipTable.joinedAt] = Clock.System.now()
        }
        true
    }

    override suspend fun removeMember(teamId: TeamId, userId: String): Boolean = dbQuery {
        TeamMembershipTable.deleteWhere {
            (TeamMembershipTable.teamId eq UUID.fromString(teamId.value)) and (TeamMembershipTable.userId eq UUID.fromString(userId))
        } > 0
    }

    override suspend fun updateMemberRole(teamId: TeamId, userId: String, role: TeamRole): Boolean = dbQuery {
        TeamMembershipTable.update({
            (TeamMembershipTable.teamId eq UUID.fromString(teamId.value)) and (TeamMembershipTable.userId eq UUID.fromString(userId))
        }) { it[TeamMembershipTable.role] = role } > 0
    }

    override suspend fun findMembers(teamId: TeamId): List<TeamMembership> = dbQuery {
        val teamUuid = UUID.fromString(teamId.value)
        val rows = TeamMembershipTable.selectAll()
            .where { TeamMembershipTable.teamId eq teamUuid }
            .toList()
        val userIds = rows.map { it[TeamMembershipTable.userId].value.toString() }.toSet()
        val usernameMap = batchLookupUsernames(userIds)
        rows.map { row ->
            val uid = row[TeamMembershipTable.userId].value.toString()
            TeamMembership(
                teamId = TeamId(row[TeamMembershipTable.teamId].value.toString()),
                userId = UserId(uid),
                username = usernameMap[uid] ?: "",
                role = row[TeamMembershipTable.role],
                joinedAt = row[TeamMembershipTable.joinedAt].toEpochMilliseconds(),
            )
        }
    }

    override suspend fun findMembership(teamId: TeamId, userId: String): TeamMembership? = dbQuery {
        TeamMembershipTable.selectAll()
            .where { (TeamMembershipTable.teamId eq UUID.fromString(teamId.value)) and (TeamMembershipTable.userId eq UUID.fromString(userId)) }
            .singleOrNull()?.let { row ->
                val uid = row[TeamMembershipTable.userId].value.toString()
                val usernameMap = batchLookupUsernames(setOf(uid))
                TeamMembership(
                    teamId = TeamId(row[TeamMembershipTable.teamId].value.toString()),
                    userId = UserId(uid),
                    username = usernameMap[uid] ?: "",
                    role = row[TeamMembershipTable.role],
                    joinedAt = row[TeamMembershipTable.joinedAt].toEpochMilliseconds(),
                )
            }
    }

    override suspend fun countMembersWithRole(teamId: TeamId, role: TeamRole): Long = dbQuery {
        TeamMembershipTable.selectAll()
            .where { (TeamMembershipTable.teamId eq UUID.fromString(teamId.value)) and (TeamMembershipTable.role eq role) }
            .count()
    }

    override suspend fun getMemberCount(teamId: TeamId): Int = dbQuery {
        TeamMembershipTable.selectAll()
            .where { TeamMembershipTable.teamId eq UUID.fromString(teamId.value) }
            .count().toInt()
    }

    override suspend fun getMemberCounts(teamIds: List<TeamId>): Map<TeamId, Int> = dbQuery {
        if (teamIds.isEmpty()) return@dbQuery emptyMap()
        val teamUuids = teamIds.mapNotNull { runCatching { UUID.fromString(it.value) }.getOrNull() }
        TeamMembershipTable
            .select(TeamMembershipTable.teamId, TeamMembershipTable.teamId.count())
            .where { TeamMembershipTable.teamId inList teamUuids }
            .groupBy(TeamMembershipTable.teamId)
            .associate { row ->
                TeamId(row[TeamMembershipTable.teamId].value.toString()) to row[TeamMembershipTable.teamId.count()].toInt()
            }
    }

    override suspend fun updateMemberRoleAtomic(teamId: TeamId, userId: String, newRole: TeamRole): Boolean = dbQuery {
        val teamUuid = UUID.fromString(teamId.value)
        val userUuid = UUID.fromString(userId)
        val allMembers = lockTeamMembersForUpdate(teamUuid, userUuid)

        if (newRole != TeamRole.TEAM_LEAD) {
            requireNotLastLead(allMembers, userUuid, "Cannot demote the last team lead")
        }

        TeamMembershipTable.update({
            (TeamMembershipTable.teamId eq teamUuid) and (TeamMembershipTable.userId eq userUuid)
        }) { it[TeamMembershipTable.role] = newRole } > 0
    }

    override suspend fun removeMemberAtomic(teamId: TeamId, userId: String): Boolean = dbQuery {
        val teamUuid = UUID.fromString(teamId.value)
        val userUuid = UUID.fromString(userId)
        requireNotLastLead(lockTeamMembersForUpdate(teamUuid, userUuid), userUuid, "Cannot remove the last team lead")

        TeamMembershipTable.deleteWhere {
            (TeamMembershipTable.teamId eq teamUuid) and (TeamMembershipTable.userId eq userUuid)
        } > 0
    }

    /**
     * Locks all membership rows for a team (FOR UPDATE) and validates the target user is a member.
     * Prevents concurrent modifications from racing on lead count checks.
     */
    private fun lockTeamMembersForUpdate(teamUuid: UUID, userUuid: UUID): List<ResultRow> {
        val allMembers = TeamMembershipTable.selectAll()
            .where { TeamMembershipTable.teamId eq teamUuid }
            .forUpdate()
            .toList()
        allMembers.find { it[TeamMembershipTable.userId].value == userUuid }
            ?: throw NotFoundException("Member not found")
        return allMembers
    }

    private fun requireNotLastLead(allMembers: List<ResultRow>, userUuid: UUID, errorMessage: String) {
        val currentRole = allMembers.first { it[TeamMembershipTable.userId].value == userUuid }[TeamMembershipTable.role]
        if (currentRole == TeamRole.TEAM_LEAD) {
            val leadCount = allMembers.count { it[TeamMembershipTable.role] == TeamRole.TEAM_LEAD }
            if (leadCount <= 1) {
                throw IllegalArgumentException(errorMessage)
            }
        }
    }

    private fun insertMembershipOrThrowConflict(teamUuid: UUID, userUuid: UUID, role: TeamRole) {
        try {
            TeamMembershipTable.insert {
                it[TeamMembershipTable.teamId] = teamUuid
                it[TeamMembershipTable.userId] = userUuid
                it[TeamMembershipTable.role] = role
                it[TeamMembershipTable.joinedAt] = Clock.System.now()
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            if (isUniqueConstraintViolation(e)) {
                throw IllegalArgumentException("Already a member of this team")
            }
            throw e
        }
    }

    override suspend fun acceptInviteAtomic(inviteId: String, userId: String, role: TeamRole): TeamId = dbQuery {
        val inviteUuid = UUID.fromString(inviteId)
        val userUuid = UUID.fromString(userId)

        // Lock and validate invite in one transaction
        val invite = TeamInviteTable.selectAll()
            .where { TeamInviteTable.id eq inviteUuid }
            .forUpdate()
            .singleOrNull() ?: throw NotFoundException("Invite not found")

        if (invite[TeamInviteTable.invitedUserId].value != userUuid) {
            throw ForbiddenException("This invite is not for you")
        }
        if (invite[TeamInviteTable.status] != InviteStatus.PENDING) {
            throw IllegalArgumentException("Invite is not pending")
        }

        // TEAM-H5: Check invite expiry
        val expiresAt = invite[TeamInviteTable.expiresAt]
        if (expiresAt != null && Clock.System.now() > expiresAt) {
            // Auto-expire the invite
            TeamInviteTable.update({ TeamInviteTable.id eq inviteUuid }) {
                it[TeamInviteTable.status] = InviteStatus.CANCELLED
            }
            throw IllegalArgumentException("Invite has expired")
        }

        val teamUuid = invite[TeamInviteTable.teamId].value

        // Check not already a member (within same transaction)
        val existingMember = TeamMembershipTable.selectAll()
            .where { (TeamMembershipTable.teamId eq teamUuid) and (TeamMembershipTable.userId eq userUuid) }
            .singleOrNull()
        if (existingMember != null) {
            throw IllegalArgumentException("Already a member of this team")
        }

        // Update invite status
        TeamInviteTable.update({ TeamInviteTable.id eq inviteUuid }) {
            it[TeamInviteTable.status] = InviteStatus.ACCEPTED
        }

        insertMembershipOrThrowConflict(teamUuid, userUuid, role)

        TeamId(teamUuid.toString())
    }

    override suspend fun approveJoinRequestAtomic(
        teamId: TeamId,
        requestId: String,
        reviewedByUserId: String,
        role: TeamRole,
    ): String = dbQuery {
        val requestUuid = UUID.fromString(requestId)
        val teamUuid = UUID.fromString(teamId.value)
        val reviewerUuid = UUID.fromString(reviewedByUserId)

        // Lock and validate join request in one transaction
        val request = JoinRequestTable.selectAll()
            .where { JoinRequestTable.id eq requestUuid }
            .forUpdate()
            .singleOrNull() ?: throw NotFoundException("Join request not found")

        if (request[JoinRequestTable.teamId].value != teamUuid) {
            throw ForbiddenException("Request does not belong to this team")
        }
        if (request[JoinRequestTable.status] != JoinRequestStatus.PENDING) {
            throw IllegalArgumentException("Request is not pending")
        }

        val requestUserId = request[JoinRequestTable.userId].value

        // Check not already a member (within same transaction)
        val existingMember = TeamMembershipTable.selectAll()
            .where { (TeamMembershipTable.teamId eq teamUuid) and (TeamMembershipTable.userId eq requestUserId) }
            .singleOrNull()
        if (existingMember != null) {
            throw IllegalArgumentException("Already a member of this team")
        }

        // Update request status
        JoinRequestTable.update({ JoinRequestTable.id eq requestUuid }) {
            it[JoinRequestTable.status] = JoinRequestStatus.APPROVED
            it[JoinRequestTable.reviewedByUserId] = reviewerUuid
            it[JoinRequestTable.reviewedAt] = Clock.System.now()
        }

        insertMembershipOrThrowConflict(teamUuid, requestUserId, role)

        requestUserId.toString()
    }

    override suspend fun getUserTeams(userId: String): List<Pair<Team, TeamRole>> = dbQuery {
        val userUuid = UUID.fromString(userId)
        val rows = TeamMembershipTable.selectAll()
            .where { TeamMembershipTable.userId eq userUuid }
            .toList()
        if (rows.isEmpty()) return@dbQuery emptyList()
        val teamUuids = rows.map { it[TeamMembershipTable.teamId].value }.toSet()
        val teamMap = TeamTable.selectAll()
            .where { TeamTable.id inList teamUuids }
            .associate { it[TeamTable.id].value.toString() to it.toTeam() }
        rows.mapNotNull { row ->
            val tid = row[TeamMembershipTable.teamId].value.toString()
            val team = teamMap[tid] ?: return@mapNotNull null
            team to row[TeamMembershipTable.role]
        }
    }

    // Invites

    override suspend fun createInvite(teamId: TeamId, invitedUserId: String, invitedByUserId: String): TeamInvite = dbQuery {
        val now = Clock.System.now()
        val id = UUID.randomUUID()
        // TEAM-H5: Set invite expiry (7 days)
        val expiry = now + INVITE_EXPIRY_DURATION
        TeamInviteTable.insert {
            it[TeamInviteTable.id] = id
            it[TeamInviteTable.teamId] = UUID.fromString(teamId.value)
            it[TeamInviteTable.invitedUserId] = UUID.fromString(invitedUserId)
            it[TeamInviteTable.invitedByUserId] = UUID.fromString(invitedByUserId)
            it[TeamInviteTable.status] = InviteStatus.PENDING
            it[TeamInviteTable.createdAt] = now
            it[TeamInviteTable.expiresAt] = expiry
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
        val now = Clock.System.now()
        val rows = TeamInviteTable.selectAll()
            .where {
                (TeamInviteTable.teamId eq UUID.fromString(teamId.value)) and
                    (TeamInviteTable.status eq InviteStatus.PENDING) and
                    ((TeamInviteTable.expiresAt.isNull()) or (TeamInviteTable.expiresAt greater now))
            }
            .toList()
        mapInviteRows(rows)
    }

    override suspend fun findPendingInvitesByUser(userId: String): List<TeamInvite> = dbQuery {
        val now = Clock.System.now()
        val rows = TeamInviteTable.selectAll()
            .where {
                (TeamInviteTable.invitedUserId eq UUID.fromString(userId)) and
                    (TeamInviteTable.status eq InviteStatus.PENDING) and
                    ((TeamInviteTable.expiresAt.isNull()) or (TeamInviteTable.expiresAt greater now))
            }
            .toList()
        mapInviteRows(rows)
    }

    override suspend fun updateInviteStatus(id: String, status: InviteStatus): Boolean = dbQuery {
        TeamInviteTable.update({ TeamInviteTable.id eq UUID.fromString(id) }) {
            it[TeamInviteTable.status] = status
        } > 0
    }

    override suspend fun findExistingPendingInvite(teamId: TeamId, userId: String): TeamInvite? = dbQuery {
        val now = Clock.System.now()
        val row = TeamInviteTable.selectAll()
            .where {
                (TeamInviteTable.teamId eq UUID.fromString(teamId.value)) and
                    (TeamInviteTable.invitedUserId eq UUID.fromString(userId)) and
                    (TeamInviteTable.status eq InviteStatus.PENDING) and
                    // TEAM-H5: Exclude expired invites
                    ((TeamInviteTable.expiresAt.isNull()) or (TeamInviteTable.expiresAt greater now))
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
            allTeamIds.add(row[TeamInviteTable.teamId].value.toString())
            allUserIds.add(row[TeamInviteTable.invitedUserId].value.toString())
            allUserIds.add(row[TeamInviteTable.invitedByUserId].value.toString())
        }
        val usernameMap = batchLookupUsernames(allUserIds)
        val teamNameMap = batchLookupTeamNames(allTeamIds)
        return rows.map { row ->
            val tid = row[TeamInviteTable.teamId].value.toString()
            val invitedUid = row[TeamInviteTable.invitedUserId].value.toString()
            val invitedByUid = row[TeamInviteTable.invitedByUserId].value.toString()
            TeamInvite(
                id = row[TeamInviteTable.id].value.toString(),
                teamId = TeamId(tid),
                teamName = teamNameMap[tid] ?: "",
                invitedUserId = UserId(invitedUid),
                invitedUsername = usernameMap[invitedUid] ?: "",
                invitedByUserId = UserId(invitedByUid),
                invitedByUsername = usernameMap[invitedByUid] ?: "",
                status = row[TeamInviteTable.status],
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
            it[JoinRequestTable.teamId] = UUID.fromString(teamId.value)
            it[JoinRequestTable.userId] = UUID.fromString(userId)
            it[JoinRequestTable.status] = JoinRequestStatus.PENDING
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
            .where { (JoinRequestTable.teamId eq UUID.fromString(teamId.value)) and (JoinRequestTable.status eq JoinRequestStatus.PENDING) }
            .toList()
        mapJoinRequestRows(rows)
    }

    override suspend fun updateJoinRequestStatus(id: String, status: JoinRequestStatus, reviewedByUserId: String): Boolean = dbQuery {
        JoinRequestTable.update({ JoinRequestTable.id eq UUID.fromString(id) }) {
            it[JoinRequestTable.status] = status
            it[JoinRequestTable.reviewedByUserId] = UUID.fromString(reviewedByUserId)
            it[JoinRequestTable.reviewedAt] = Clock.System.now()
        } > 0
    }

    override suspend fun findExistingPendingJoinRequest(teamId: TeamId, userId: String): JoinRequest? = dbQuery {
        val row = JoinRequestTable.selectAll()
            .where {
                (JoinRequestTable.teamId eq UUID.fromString(teamId.value)) and
                    (JoinRequestTable.userId eq UUID.fromString(userId)) and
                    (JoinRequestTable.status eq JoinRequestStatus.PENDING)
            }
            .singleOrNull() ?: return@dbQuery null
        mapJoinRequestRows(listOf(row)).firstOrNull()
    }

    // TEAM-L3: Cancel pending join requests when user joins via invite
    override suspend fun cancelPendingJoinRequest(teamId: TeamId, userId: String, reviewedByUserId: String?): Unit = dbQuery {
        JoinRequestTable.update({
            (JoinRequestTable.teamId eq UUID.fromString(teamId.value)) and
                (JoinRequestTable.userId eq UUID.fromString(userId)) and
                (JoinRequestTable.status eq JoinRequestStatus.PENDING)
        }) {
            it[JoinRequestTable.status] = JoinRequestStatus.REJECTED
            it[JoinRequestTable.reviewedAt] = Clock.System.now()
            if (reviewedByUserId != null) {
                it[JoinRequestTable.reviewedByUserId] = UUID.fromString(reviewedByUserId)
            }
        }
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
            allTeamIds.add(row[JoinRequestTable.teamId].value.toString())
            allUserIds.add(row[JoinRequestTable.userId].value.toString())
            row[JoinRequestTable.reviewedByUserId]?.let { entityId -> allUserIds.add(entityId.value.toString()) }
        }
        val usernameMap = batchLookupUsernames(allUserIds)
        val teamNameMap = batchLookupTeamNames(allTeamIds)
        return rows.map { row ->
            val tid = row[JoinRequestTable.teamId].value.toString()
            val uid = row[JoinRequestTable.userId].value.toString()
            val reviewerId = row[JoinRequestTable.reviewedByUserId]?.value?.toString()
            JoinRequest(
                id = row[JoinRequestTable.id].value.toString(),
                teamId = TeamId(tid),
                teamName = teamNameMap[tid] ?: "",
                userId = UserId(uid),
                username = usernameMap[uid] ?: "",
                status = row[JoinRequestTable.status],
                reviewedByUserId = reviewerId?.let { UserId(it) },
                reviewedByUsername = reviewerId?.let { usernameMap[it] ?: "" },
                createdAt = row[JoinRequestTable.createdAt].toEpochMilliseconds(),
                reviewedAt = row[JoinRequestTable.reviewedAt]?.toEpochMilliseconds(),
            )
        }
    }
}
