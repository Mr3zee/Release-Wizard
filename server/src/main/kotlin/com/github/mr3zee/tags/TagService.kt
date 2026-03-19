package com.github.mr3zee.tags

import com.github.mr3zee.api.TagInfo
import com.github.mr3zee.audit.AuditService
import com.github.mr3zee.auth.UserSession
import com.github.mr3zee.model.AuditAction
import com.github.mr3zee.model.AuditTargetType
import com.github.mr3zee.model.TeamId
import org.slf4j.LoggerFactory

interface TagService {
    suspend fun listAllTags(): List<TagInfo>
    suspend fun listTagsByTeam(teamId: String): List<TagInfo>
    suspend fun renameTag(oldName: String, newName: String, teamId: String? = null, session: UserSession? = null): Long
    suspend fun deleteTag(name: String, teamId: String? = null, session: UserSession? = null): Long
}

class DefaultTagService(
    private val repository: TagRepository,
    private val auditService: AuditService,
) : TagService {
    private val log = LoggerFactory.getLogger(DefaultTagService::class.java)

    override suspend fun listAllTags(): List<TagInfo> {
        return repository.findAllTagsWithCount().map { (name, count) ->
            TagInfo(name = name, count = count)
        }
    }

    override suspend fun listTagsByTeam(teamId: String): List<TagInfo> {
        return repository.findTagsWithCountByTeam(teamId).map { (name, count) ->
            TagInfo(name = name, count = count)
        }
    }

    override suspend fun renameTag(oldName: String, newName: String, teamId: String?, session: UserSession?): Long {
        require(newName.isNotBlank()) { "New tag name must not be blank" }
        val count = repository.renameTag(oldName, newName, teamId)
        log.info("Tag renamed: '{}' -> '{}' ({} updated, team={})", oldName, newName, count, teamId ?: "all")
        if (session != null && count > 0) {
            auditService.log(
                teamId?.let { TeamId(it) },
                session,
                AuditAction.TAG_RENAMED,
                AuditTargetType.TAG,
                oldName,
                "Renamed tag '$oldName' -> '$newName' ($count releases updated)",
            )
        }
        return count
    }

    override suspend fun deleteTag(name: String, teamId: String?, session: UserSession?): Long {
        val count = repository.deleteTag(name, teamId)
        if (count > 0) {
            log.info("Tag deleted: '{}' ({} removed, team={})", name, count, teamId ?: "all")
            if (session != null) {
                auditService.log(
                    teamId?.let { TeamId(it) },
                    session,
                    AuditAction.TAG_DELETED,
                    AuditTargetType.TAG,
                    name,
                    "Deleted tag '$name' ($count releases affected)",
                )
            }
        }
        return count
    }
}
