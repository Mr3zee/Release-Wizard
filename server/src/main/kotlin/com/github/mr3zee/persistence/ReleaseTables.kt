package com.github.mr3zee.persistence

import com.github.mr3zee.AppJson
import com.github.mr3zee.model.BlockApproval
import com.github.mr3zee.model.BlockStatus
import com.github.mr3zee.model.DagGraph
import com.github.mr3zee.model.GatePhase
import com.github.mr3zee.model.Parameter
import com.github.mr3zee.model.ReleaseStatus
import com.github.mr3zee.model.SubBuild
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.json.jsonb

object ReleaseTable : UUIDTable("releases") {
    val projectTemplateId = reference("project_template_id", ProjectTemplateTable, onDelete = ReferenceOption.RESTRICT)
    val status = enumerationByName<ReleaseStatus>("status", 32)
    val dagSnapshot = jsonb<DagGraph>("dag_snapshot", AppJson)
    val parameters = jsonb("parameters", AppJson, ListSerializer(Parameter.serializer()))
    val teamId = reference("team_id", TeamTable, onDelete = ReferenceOption.RESTRICT)
    val createdAt = timestamp("created_at").nullable()
    val startedAt = timestamp("started_at").nullable()
    val finishedAt = timestamp("finished_at").nullable()

    init {
        index(false, status)
        index(false, projectTemplateId)
        index(false, teamId)
    }
}

object BlockExecutionTable : UUIDTable("block_executions") {
    val releaseId = reference("release_id", ReleaseTable, onDelete = ReferenceOption.CASCADE)
    val blockId = varchar("block_id", 255)
    val status = enumerationByName<BlockStatus>("status", 32)
    val outputs = jsonb("outputs", AppJson, MapSerializer(String.serializer(), String.serializer()))
    val error = text("error").nullable()
    val startedAt = timestamp("started_at").nullable()
    val finishedAt = timestamp("finished_at").nullable()
    val approvals = jsonb("approvals", AppJson, ListSerializer(BlockApproval.serializer()))
    val gatePhase = enumerationByName<GatePhase>("gate_phase", 32).nullable()
    val gateMessage = text("gate_message").nullable()
    val webhookStatus = varchar("webhook_status", 200).nullable()
    val webhookStatusDescription = text("webhook_status_description").nullable()
    val webhookStatusAt = timestamp("webhook_status_at").nullable()
    val subBuilds = jsonb("sub_builds", AppJson, ListSerializer(SubBuild.serializer())).default(emptyList())

    init {
        index(false, releaseId)
        uniqueIndex(releaseId, blockId)
    }
}
