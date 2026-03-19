package com.github.mr3zee.tags

import com.github.mr3zee.api.TagInfo
import org.slf4j.LoggerFactory

interface TagService {
    suspend fun listAllTags(): List<TagInfo>
    suspend fun listTagsByTeam(teamId: String): List<TagInfo>
    suspend fun renameTag(oldName: String, newName: String, teamId: String? = null): Long
    suspend fun deleteTag(name: String, teamId: String? = null): Long
}

class DefaultTagService(private val repository: TagRepository) : TagService {
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

    override suspend fun renameTag(oldName: String, newName: String, teamId: String?): Long {
        require(newName.isNotBlank()) { "New tag name must not be blank" }
        val count = repository.renameTag(oldName, newName, teamId)
        log.info("Tag renamed: '{}' -> '{}' ({} updated, team={})", oldName, newName, count, teamId ?: "all")
        return count
    }

    override suspend fun deleteTag(name: String, teamId: String?): Long {
        val count = repository.deleteTag(name, teamId)
        if (count > 0) {
            log.info("Tag deleted: '{}' ({} removed, team={})", name, count, teamId ?: "all")
        }
        return count
    }
}
