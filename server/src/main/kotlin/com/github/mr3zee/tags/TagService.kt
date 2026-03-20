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

/** TAG-H4: Shared tag validation constants — single source of truth */
object TagValidation {
    const val MAX_TAG_LENGTH = 100
    const val MAX_TAGS_PER_RELEASE = 20
    val TAG_PATTERN = Regex("^[a-z0-9_.\\-]+$")

    fun validateTagName(tag: String) {
        require(tag.length <= MAX_TAG_LENGTH) { "Tag name must not exceed $MAX_TAG_LENGTH characters" }
        require(TAG_PATTERN.matches(tag)) { "Tag name '$tag' contains invalid characters (allowed: a-z, 0-9, dot, hyphen, underscore)" }
    }
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
        // TAG-H4: Validate both old and new tag names after normalization
        val normalizedOld = oldName.trim().lowercase()
        val normalizedNew = newName.trim().lowercase()
        TagValidation.validateTagName(normalizedOld)
        TagValidation.validateTagName(normalizedNew)
        val count = repository.renameTag(normalizedOld, normalizedNew, teamId)
        log.info("Tag renamed: '{}' -> '{}' ({} updated, team={})", normalizedOld, normalizedNew, count, teamId ?: "all")
        if (session != null && count > 0) {
            auditService.log(
                teamId?.let { TeamId(it) },
                session,
                AuditAction.TAG_RENAMED,
                AuditTargetType.TAG,
                normalizedOld,
                "Renamed tag '$normalizedOld' -> '$normalizedNew' ($count releases updated)",
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
