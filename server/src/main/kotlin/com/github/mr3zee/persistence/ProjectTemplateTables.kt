package com.github.mr3zee.persistence

import com.github.mr3zee.AppJson
import com.github.mr3zee.model.DagGraph
import com.github.mr3zee.model.Parameter
import kotlinx.serialization.builtins.ListSerializer
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object ProjectTemplateTable : UUIDTable("project_templates") {
    val name = varchar("name", 255)
    val description = text("description").default("")
    val dagGraph = jsonb<DagGraph>("dag_graph", AppJson)
    val parameters = jsonb("parameters", AppJson, ListSerializer(Parameter.serializer()))
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}
