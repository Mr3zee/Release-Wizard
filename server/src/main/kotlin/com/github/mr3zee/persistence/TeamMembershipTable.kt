package com.github.mr3zee.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object TeamMembershipTable : Table("team_memberships") {
    val teamId = varchar("team_id", 36)
    val userId = varchar("user_id", 36)
    val role = varchar("role", 32)
    val joinedAt = timestamp("joined_at")

    override val primaryKey = PrimaryKey(teamId, userId)

    init {
        index(false, userId)
    }
}
