package com.github.mr3zee.persistence

import com.github.mr3zee.AppJson
import com.github.mr3zee.model.DagGraph
import com.github.mr3zee.model.Parameter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.json.jsonb

object ReleaseTable : UUIDTable("releases") {
    val projectTemplateId = varchar("project_template_id", 36)
    val status = varchar("status", 32)
    val dagSnapshot = jsonb<DagGraph>("dag_snapshot", AppJson)
    val parameters = jsonb("parameters", AppJson, ListSerializer(Parameter.serializer()))
    val startedAt = timestamp("started_at").nullable()
    val finishedAt = timestamp("finished_at").nullable()
}

object BlockExecutionTable : UUIDTable("block_executions") {
    val releaseId = varchar("release_id", 36)
    val blockId = varchar("block_id", 255)
    val status = varchar("status", 32)
    val outputs = jsonb("outputs", AppJson, MapSerializer(String.serializer(), String.serializer()))
    val error = text("error").nullable()
    val startedAt = timestamp("started_at").nullable()
    val finishedAt = timestamp("finished_at").nullable()
}
