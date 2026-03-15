package com.github.mr3zee.persistence

import com.github.mr3zee.model.TeamRole
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object TeamMembershipTable : Table("team_memberships") {
    val teamId = reference("team_id", TeamTable, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val role = enumerationByName<TeamRole>("role", 32)
    val joinedAt = timestamp("joined_at")

    override val primaryKey = PrimaryKey(teamId, userId)

    init {
        index(false, userId)
    }
}
