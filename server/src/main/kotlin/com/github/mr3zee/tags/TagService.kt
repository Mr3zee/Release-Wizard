package com.github.mr3zee.tags

import com.github.mr3zee.api.TagInfo

interface TagService {
    suspend fun listAllTags(): List<TagInfo>
    suspend fun listTagsByTeam(teamId: String): List<TagInfo>
    suspend fun renameTag(oldName: String, newName: String, teamId: String? = null): Long
    suspend fun deleteTag(name: String, teamId: String? = null): Long
}

class DefaultTagService(private val repository: TagRepository) : TagService {
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
        return repository.renameTag(oldName, newName, teamId)
    }

    override suspend fun deleteTag(name: String, teamId: String?): Long {
        return repository.deleteTag(name, teamId)
    }
}
