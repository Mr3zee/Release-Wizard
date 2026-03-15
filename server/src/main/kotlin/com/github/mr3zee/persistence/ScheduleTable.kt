package com.github.mr3zee.persistence

import com.github.mr3zee.AppJson
import com.github.mr3zee.model.Parameter
import kotlinx.serialization.builtins.ListSerializer
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.json.jsonb

object ScheduleTable : UUIDTable("schedules") {
    val projectId = reference("project_id", ProjectTemplateTable, onDelete = ReferenceOption.CASCADE)
    val cronExpression = varchar("cron_expression", 255)
    val parameters = jsonb("parameters", AppJson, ListSerializer(Parameter.serializer()))
    val enabled = bool("enabled").default(true)
    val createdBy = reference("created_by", UserTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val nextRunAt = timestamp("next_run_at").nullable()
    val lastRunAt = timestamp("last_run_at").nullable()

    init {
        index(false, projectId)
        index("idx_schedule_enabled_next_run", false, enabled, nextRunAt)
    }
}
