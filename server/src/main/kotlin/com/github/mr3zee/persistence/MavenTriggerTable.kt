package com.github.mr3zee.persistence

import com.github.mr3zee.AppJson
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.json.jsonb

object MavenTriggerTable : UUIDTable("maven_triggers") {
    val projectId = reference("project_id", ProjectTemplateTable, onDelete = ReferenceOption.CASCADE)
    val repoUrl = varchar("repo_url", 512)
    val groupId = varchar("group_id", 255)
    val artifactId = varchar("artifact_id", 255)
    val parameterKey = varchar("parameter_key", 255)
    val enabled = bool("enabled").default(true)
    val includeSnapshots = bool("include_snapshots").default(false)
    val knownVersions = jsonb("known_versions", AppJson, SetSerializer(String.serializer()))
    val lastCheckedAt = timestamp("last_checked_at").nullable()
    val createdBy = reference("created_by", UserTable, onDelete = ReferenceOption.SET_NULL).nullable()

    init {
        index(false, projectId)
        index("idx_maven_trigger_enabled_checked", false, enabled, lastCheckedAt)
    }
}
