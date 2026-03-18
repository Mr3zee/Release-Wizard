package com.github.mr3zee.mavenpublication

import com.github.mr3zee.model.MavenTrigger
import com.github.mr3zee.model.ProjectId
import kotlin.time.Instant

data class MavenTriggerWithVersions(
    val trigger: MavenTrigger,
    val knownVersions: Set<String>,
)

interface MavenTriggerRepository {
    suspend fun findByProjectId(projectId: ProjectId): List<MavenTrigger>
    suspend fun findById(id: String): MavenTrigger?
    suspend fun findAllEnabled(): List<MavenTriggerWithVersions>
    suspend fun create(
        projectId: ProjectId,
        repoUrl: String,
        groupId: String,
        artifactId: String,
        parameterKey: String,
        includeSnapshots: Boolean,
        enabled: Boolean,
        knownVersions: Set<String>,
        createdBy: String,
    ): MavenTrigger
    suspend fun updateEnabled(id: String, enabled: Boolean): MavenTrigger?
    suspend fun updateKnownVersions(id: String, versions: Set<String>, checkedAt: Instant)
    suspend fun delete(id: String): Boolean
}
