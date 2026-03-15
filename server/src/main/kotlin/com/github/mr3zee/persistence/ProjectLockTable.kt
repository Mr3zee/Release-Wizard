package com.github.mr3zee.persistence

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object ProjectLockTable : Table("project_locks") {
    val projectId = reference("project_id", ProjectTemplateTable, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val username = varchar("username", 255)
    val acquiredAt = timestamp("acquired_at")
    val expiresAt = timestamp("expires_at")

    override val primaryKey = PrimaryKey(projectId)

    init {
        index(false, expiresAt)
    }
}
