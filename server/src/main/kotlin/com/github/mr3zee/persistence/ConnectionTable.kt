package com.github.mr3zee.persistence

import com.github.mr3zee.model.ConnectionType
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.datetime.timestamp

object ConnectionTable : UUIDTable("connections") {
    val name = varchar("name", 255)
    val type = enumerationByName<ConnectionType>("type", 32)
    val encryptedConfig = text("encrypted_config")
    val teamId = reference("team_id", TeamTable, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    init {
        index(false, teamId)
    }
}
