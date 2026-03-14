package com.github.mr3zee.tags

import com.github.mr3zee.api.TagInfo

interface TagService {
    suspend fun listAllTags(): List<TagInfo>
    suspend fun renameTag(oldName: String, newName: String): Long
    suspend fun deleteTag(name: String): Long
}

class DefaultTagService(private val repository: TagRepository) : TagService {
    override suspend fun listAllTags(): List<TagInfo> {
        return repository.findAllTagsWithCount().map { (name, count) ->
            TagInfo(name = name, count = count)
        }
    }

    override suspend fun renameTag(oldName: String, newName: String): Long {
        require(newName.isNotBlank()) { "New tag name must not be blank" }
        return repository.renameTag(oldName, newName)
    }

    override suspend fun deleteTag(name: String): Long {
        return repository.deleteTag(name)
    }
}
