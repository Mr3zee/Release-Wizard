package com.github.mr3zee.persistence

import com.github.mr3zee.model.JoinRequestStatus
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.datetime.timestamp

object JoinRequestTable : UUIDTable("join_requests") {
    val teamId = reference("team_id", TeamTable, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val status = enumerationByName<JoinRequestStatus>("status", 32)
    val reviewedByUserId = reference("reviewed_by_user_id", UserTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val createdAt = timestamp("created_at")
    val reviewedAt = timestamp("reviewed_at").nullable()

    init {
        uniqueIndex("uq_join_request_team_user_status", teamId, userId, status)
    }
}
