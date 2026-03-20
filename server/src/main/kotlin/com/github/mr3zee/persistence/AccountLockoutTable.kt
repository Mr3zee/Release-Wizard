package com.github.mr3zee.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object AccountLockoutTable : Table("account_lockouts") {
    val username = varchar("username", 255)
    val attempts = integer("attempts").default(0)
    val lockedUntil = timestamp("locked_until").nullable()
    val lastAttemptAt = timestamp("last_attempt_at")

    override val primaryKey = PrimaryKey(username)
}
