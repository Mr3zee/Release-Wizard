package com.github.mr3zee.persistence

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.datetime.timestamp

object UserNotificationTable : UUIDTable("user_notifications") {
    val userId = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val type = varchar("type", 32)
    val teamId = varchar("team_id", 36).nullable()
    val teamName = varchar("team_name", 255).nullable()
    val title = varchar("title", 255)
    val message = text("message")
    val targetType = varchar("target_type", 32).nullable()
    val targetId = varchar("target_id", 255).nullable()
    val read = bool("read").default(false)
    val timestamp = timestamp("timestamp")

    init {
        index(false, userId, this.timestamp)
    }
}
