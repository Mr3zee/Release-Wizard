package com.github.mr3zee.persistence

import com.github.mr3zee.AppJson
import com.github.mr3zee.model.NotificationConfig
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.json.jsonb

object NotificationConfigTable : UUIDTable("notification_configs") {
    val projectId = reference("project_id", ProjectTemplateTable, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val type = varchar("type", 32)
    val config = jsonb<NotificationConfig>("config", AppJson)
    val enabled = bool("enabled").default(true)

    init {
        index(false, projectId)
    }
}
