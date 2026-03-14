package com.github.mr3zee.persistence

import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.datetime.timestamp

object TeamTable : UUIDTable("teams") {
    val name = varchar("name", 255).uniqueIndex()
    val description = text("description").default("")
    val createdAt = timestamp("created_at")
}
