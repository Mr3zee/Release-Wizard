package com.github.mr3zee.persistence

import com.github.mr3zee.model.UserRole
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.datetime.timestamp

object UserTable : UUIDTable("users") {
    // AUTH-L3: Aligned with route-level validation (max 64 chars)
    val username = varchar("username", 64).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val role = enumerationByName<UserRole>("role", 50)
    val createdAt = timestamp("created_at")
    val passwordChangedAt = timestamp("password_changed_at").nullable()
}
