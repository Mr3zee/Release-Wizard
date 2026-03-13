package com.github.mr3zee.persistence

import com.github.mr3zee.AppJson
import com.github.mr3zee.model.NotificationConfig
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.json.jsonb

object NotificationConfigTable : UUIDTable("notification_configs") {
    val projectId = varchar("project_id", 36)
    val userId = varchar("user_id", 36)
    val type = varchar("type", 32)
    val config = jsonb<NotificationConfig>("config", AppJson)
    val enabled = bool("enabled").default(true)
}
