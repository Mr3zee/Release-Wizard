package com.github.mr3zee.tags

import com.github.mr3zee.model.ReleaseId
import com.github.mr3zee.persistence.ReleaseTagTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

interface TagRepository {
    suspend fun findTagsForRelease(releaseId: ReleaseId): List<String>
    suspend fun setTagsForRelease(releaseId: ReleaseId, tags: List<String>)
    suspend fun findAllTagsWithCount(): List<Pair<String, Long>>
    suspend fun renameTag(oldName: String, newName: String): Long
    suspend fun deleteTag(name: String): Long
    suspend fun findReleaseIdsByTag(tag: String): List<String>
}

class ExposedTagRepository(private val db: Database) : TagRepository {
    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction(db) { block() } }

    override suspend fun findTagsForRelease(releaseId: ReleaseId): List<String> = dbQuery {
        ReleaseTagTable.select(ReleaseTagTable.tag)
            .where { ReleaseTagTable.releaseId eq releaseId.value }
            .orderBy(ReleaseTagTable.tag, SortOrder.ASC)
            .map { it[ReleaseTagTable.tag] }
    }

    override suspend fun setTagsForRelease(releaseId: ReleaseId, tags: List<String>) = dbQuery {
        ReleaseTagTable.deleteWhere { ReleaseTagTable.releaseId eq releaseId.value }
        for (tag in tags.map { it.trim().lowercase() }.distinct()) {
            if (tag.isNotBlank()) {
                ReleaseTagTable.insert {
                    it[ReleaseTagTable.releaseId] = releaseId.value
                    it[ReleaseTagTable.tag] = tag
                }
            }
        }
    }

    override suspend fun findAllTagsWithCount(): List<Pair<String, Long>> = dbQuery {
        val countExpr = ReleaseTagTable.releaseId.count()
        ReleaseTagTable.select(ReleaseTagTable.tag, countExpr)
            .groupBy(ReleaseTagTable.tag)
            .orderBy(ReleaseTagTable.tag, SortOrder.ASC)
            .map { it[ReleaseTagTable.tag] to it[countExpr] }
    }

    override suspend fun renameTag(oldName: String, newName: String): Long = dbQuery {
        val normalizedOld = oldName.trim().lowercase()
        val normalizedNew = newName.trim().lowercase()
        if (normalizedOld == normalizedNew) return@dbQuery 0L

        // Find releases that already have the new tag — for those, just delete the old tag
        val releasesWithNewTag = ReleaseTagTable.select(ReleaseTagTable.releaseId)
            .where { ReleaseTagTable.tag eq normalizedNew }
            .map { it[ReleaseTagTable.releaseId] }
            .toSet()

        if (releasesWithNewTag.isNotEmpty()) {
            ReleaseTagTable.deleteWhere {
                (ReleaseTagTable.tag eq normalizedOld) and (ReleaseTagTable.releaseId inList releasesWithNewTag)
            }
        }

        // Rename remaining old tags to new
        val updated = ReleaseTagTable.update({ ReleaseTagTable.tag eq normalizedOld }) {
            it[ReleaseTagTable.tag] = normalizedNew
        }
        updated.toLong()
    }

    override suspend fun deleteTag(name: String): Long = dbQuery {
        val normalized = name.trim().lowercase()
        val deleted = ReleaseTagTable.deleteWhere { ReleaseTagTable.tag eq normalized }
        deleted.toLong()
    }

    override suspend fun findReleaseIdsByTag(tag: String): List<String> = dbQuery {
        val normalized = tag.trim().lowercase()
        ReleaseTagTable.select(ReleaseTagTable.releaseId)
            .where { ReleaseTagTable.tag eq normalized }
            .map { it[ReleaseTagTable.releaseId] }
    }
}
