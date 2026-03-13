package com.github.mr3zee.persistence

import com.github.mr3zee.AppJson
import com.github.mr3zee.model.Parameter
import kotlinx.serialization.builtins.ListSerializer
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.json.jsonb

object TriggerTable : UUIDTable("triggers") {
    val projectId = varchar("project_id", 36)
    val secret = varchar("secret", 255)
    val enabled = bool("enabled").default(true)
    val parametersTemplate = jsonb("parameters_template", AppJson, ListSerializer(Parameter.serializer()))
}
