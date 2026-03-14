package com.github.mr3zee.persistence

import com.github.mr3zee.AppJson
import com.github.mr3zee.model.DagGraph
import com.github.mr3zee.model.Parameter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.json.jsonb
import org.jetbrains.exposed.v1.datetime.timestamp

object ProjectTemplateTable : UUIDTable("project_templates") {
    val name = varchar("name", 255)
    val description = text("description").default("")
    val dagGraph = jsonb<DagGraph>("dag_graph", AppJson)
    val parameters = jsonb("parameters", AppJson, ListSerializer(Parameter.serializer()))
    val defaultTags = jsonb("default_tags", AppJson, ListSerializer(String.serializer()))
    val teamId = varchar("team_id", 36)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    init {
        index(false, teamId)
    }
}
