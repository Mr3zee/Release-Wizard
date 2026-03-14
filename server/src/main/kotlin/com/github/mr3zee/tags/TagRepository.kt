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
    suspend fun setTagsForRelease(releaseId: ReleaseId, tags: List<String>, teamId: String = "")
    suspend fun findAllTagsWithCount(): List<Pair<String, Long>>
    suspend fun findTagsWithCountByTeam(teamId: String): List<Pair<String, Long>>
    suspend fun renameTag(oldName: String, newName: String, teamId: String? = null): Long
    suspend fun deleteTag(name: String, teamId: String? = null): Long
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

    override suspend fun setTagsForRelease(releaseId: ReleaseId, tags: List<String>, teamId: String) = dbQuery {
        ReleaseTagTable.deleteWhere { ReleaseTagTable.releaseId eq releaseId.value }
        for (tag in tags.map { it.trim().lowercase() }.distinct()) {
            if (tag.isNotBlank()) {
                ReleaseTagTable.insert {
                    it[ReleaseTagTable.releaseId] = releaseId.value
                    it[ReleaseTagTable.tag] = tag
                    it[ReleaseTagTable.teamId] = teamId
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

    override suspend fun findTagsWithCountByTeam(teamId: String): List<Pair<String, Long>> = dbQuery {
        val countExpr = ReleaseTagTable.releaseId.count()
        ReleaseTagTable.select(ReleaseTagTable.tag, countExpr)
            .where { ReleaseTagTable.teamId eq teamId }
            .groupBy(ReleaseTagTable.tag)
            .orderBy(ReleaseTagTable.tag, SortOrder.ASC)
            .map { it[ReleaseTagTable.tag] to it[countExpr] }
    }

    override suspend fun renameTag(oldName: String, newName: String, teamId: String?): Long = dbQuery {
        val normalizedOld = oldName.trim().lowercase()
        val normalizedNew = newName.trim().lowercase()
        if (normalizedOld == normalizedNew) return@dbQuery 0L

        fun baseCondition(): Op<Boolean> {
            val cond: Op<Boolean> = ReleaseTagTable.tag eq normalizedOld
            return if (teamId != null) cond and (ReleaseTagTable.teamId eq teamId) else cond
        }

        val releasesWithNewTag = ReleaseTagTable.select(ReleaseTagTable.releaseId)
            .where { ReleaseTagTable.tag eq normalizedNew }
            .map { it[ReleaseTagTable.releaseId] }
            .toSet()

        if (releasesWithNewTag.isNotEmpty()) {
            ReleaseTagTable.deleteWhere {
                baseCondition() and (ReleaseTagTable.releaseId inList releasesWithNewTag)
            }
        }

        val updated = ReleaseTagTable.update({ baseCondition() }) {
            it[ReleaseTagTable.tag] = normalizedNew
        }
        updated.toLong()
    }

    override suspend fun deleteTag(name: String, teamId: String?): Long = dbQuery {
        val normalized = name.trim().lowercase()
        val deleted = ReleaseTagTable.deleteWhere {
            val cond: Op<Boolean> = ReleaseTagTable.tag eq normalized
            if (teamId != null) cond and (ReleaseTagTable.teamId eq teamId) else cond
        }
        deleted.toLong()
    }

    override suspend fun findReleaseIdsByTag(tag: String): List<String> = dbQuery {
        val normalized = tag.trim().lowercase()
        ReleaseTagTable.select(ReleaseTagTable.releaseId)
            .where { ReleaseTagTable.tag eq normalized }
            .map { it[ReleaseTagTable.releaseId] }
    }
}
