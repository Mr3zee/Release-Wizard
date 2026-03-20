package com.github.mr3zee.tags

import com.github.mr3zee.model.ReleaseId
import com.github.mr3zee.persistence.ReleaseTagTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.sql.Connection
import java.util.UUID

interface TagRepository {
    suspend fun findTagsForRelease(releaseId: ReleaseId): List<String>
    suspend fun setTagsForRelease(releaseId: ReleaseId, tags: List<String>, teamId: String)
    suspend fun findAllTagsWithCount(): List<Pair<String, Long>>
    suspend fun findTagsWithCountByTeam(teamId: String): List<Pair<String, Long>>
    suspend fun renameTag(oldName: String, newName: String, teamId: String? = null): Long
    suspend fun deleteTag(name: String, teamId: String? = null): Long
    suspend fun findReleaseIdsByTag(tag: String): List<String>
}

class ExposedTagRepository(private val db: Database) : TagRepository {
    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction(db) { block() } }

    private suspend fun <T> dbQuery(transactionIsolation: Int, block: suspend () -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction(db, transactionIsolation = transactionIsolation) { block() } }

    override suspend fun findTagsForRelease(releaseId: ReleaseId): List<String> = dbQuery {
        ReleaseTagTable.select(ReleaseTagTable.tag)
            .where { ReleaseTagTable.releaseId eq UUID.fromString(releaseId.value) }
            .orderBy(ReleaseTagTable.tag, SortOrder.ASC)
            .map { it[ReleaseTagTable.tag] }
    }

    // TAG-H2: REPEATABLE_READ isolation for delete-then-insert atomicity
    override suspend fun setTagsForRelease(releaseId: ReleaseId, tags: List<String>, teamId: String) {
        // TAG-H4: Validate all tags BEFORE entering the transaction (avoid delete-then-fail)
        require(tags.size <= TagValidation.MAX_TAGS_PER_RELEASE) { "Maximum ${TagValidation.MAX_TAGS_PER_RELEASE} tags per release" }
        val normalizedTags = tags.map { it.trim().lowercase() }.distinct().filter { it.isNotBlank() }
        for (tag in normalizedTags) {
            TagValidation.validateTagName(tag)
        }
        dbQuery(Connection.TRANSACTION_REPEATABLE_READ) {
            val releaseUuid = UUID.fromString(releaseId.value)
            val teamUuid = UUID.fromString(teamId)
            ReleaseTagTable.deleteWhere { ReleaseTagTable.releaseId eq releaseUuid }
            for (tag in normalizedTags) {
                ReleaseTagTable.insert {
                    it[ReleaseTagTable.releaseId] = releaseUuid
                    it[ReleaseTagTable.tag] = tag
                    it[ReleaseTagTable.teamId] = teamUuid
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
        val teamUuid = UUID.fromString(teamId)
        val countExpr = ReleaseTagTable.releaseId.count()
        ReleaseTagTable.select(ReleaseTagTable.tag, countExpr)
            .where { ReleaseTagTable.teamId eq teamUuid }
            .groupBy(ReleaseTagTable.tag)
            .orderBy(ReleaseTagTable.tag, SortOrder.ASC)
            .map { it[ReleaseTagTable.tag] to it[countExpr] }
    }

    // TAG-H1: SERIALIZABLE isolation to prevent TOCTOU race in conflict-check + update
    override suspend fun renameTag(oldName: String, newName: String, teamId: String?): Long = dbQuery(Connection.TRANSACTION_SERIALIZABLE) {
        val normalizedOld = oldName.trim().lowercase()
        val normalizedNew = newName.trim().lowercase()
        if (normalizedOld == normalizedNew) return@dbQuery 0L

        val teamUuid = teamId?.let { UUID.fromString(it) }

        fun baseCondition(): Op<Boolean> {
            val cond: Op<Boolean> = ReleaseTagTable.tag eq normalizedOld
            return if (teamUuid != null) cond and (ReleaseTagTable.teamId eq teamUuid) else cond
        }

        // TAG-H3: Add teamId filter to deduplication query to prevent cross-team IDOR
        val deduplicationCondition: Op<Boolean> = if (teamUuid != null) {
            (ReleaseTagTable.tag eq normalizedNew) and (ReleaseTagTable.teamId eq teamUuid)
        } else {
            ReleaseTagTable.tag eq normalizedNew
        }

        val releasesWithNewTag = ReleaseTagTable.select(ReleaseTagTable.releaseId)
            .where { deduplicationCondition }
            .map { it[ReleaseTagTable.releaseId].value }
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
        val teamUuid = teamId?.let { UUID.fromString(it) }
        val deleted = ReleaseTagTable.deleteWhere {
            val cond: Op<Boolean> = ReleaseTagTable.tag eq normalized
            if (teamUuid != null) cond and (ReleaseTagTable.teamId eq teamUuid) else cond
        }
        deleted.toLong()
    }

    override suspend fun findReleaseIdsByTag(tag: String): List<String> = dbQuery {
        val normalized = tag.trim().lowercase()
        ReleaseTagTable.select(ReleaseTagTable.releaseId)
            .where { ReleaseTagTable.tag eq normalized }
            .map { it[ReleaseTagTable.releaseId].value.toString() }
    }
}
