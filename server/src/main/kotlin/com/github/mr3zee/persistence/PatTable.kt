package com.github.mr3zee.persistence

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.datetime.timestamp

object PatTable : UUIDTable("personal_access_tokens") {
    val userId = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE).index()
    val name = varchar("name", 100)
    val tokenHash = varchar("token_hash", 64).uniqueIndex()
    val expiresAt = timestamp("expires_at").nullable()
    val lastUsedAt = timestamp("last_used_at").nullable()
    val createdAt = timestamp("created_at")
    val revokedAt = timestamp("revoked_at").nullable()
}
