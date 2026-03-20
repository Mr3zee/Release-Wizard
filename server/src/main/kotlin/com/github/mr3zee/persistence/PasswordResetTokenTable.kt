package com.github.mr3zee.persistence

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.datetime.timestamp

object PasswordResetTokenTable : UUIDTable("password_reset_tokens") {
    val tokenHash = varchar("token_hash", 64).uniqueIndex()
    val userId = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE).index()
    val createdByUserId = reference("created_by_user_id", UserTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val expiresAt = timestamp("expires_at")
    val usedAt = timestamp("used_at").nullable()
    val createdAt = timestamp("created_at")
}
