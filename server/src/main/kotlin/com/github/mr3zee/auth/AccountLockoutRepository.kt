package com.github.mr3zee.auth

import com.github.mr3zee.persistence.AccountLockoutTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlin.time.Instant

data class LockoutRecord(
    val username: String,
    val attempts: Int,
    val lockedUntil: Instant?,
    val lastAttemptAt: Instant,
)

class AccountLockoutRepository(private val db: Database) {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction(db) { block() } }

    suspend fun find(username: String): LockoutRecord? = dbQuery {
        AccountLockoutTable.selectAll()
            .where { AccountLockoutTable.username eq username }
            .singleOrNull()
            ?.toLockoutRecord()
    }

    suspend fun upsert(record: LockoutRecord): Unit = dbQuery {
        AccountLockoutTable.upsert(
            keys = arrayOf(AccountLockoutTable.username),
            onUpdate = {
                it[AccountLockoutTable.attempts] = record.attempts
                it[AccountLockoutTable.lockedUntil] = record.lockedUntil
                it[AccountLockoutTable.lastAttemptAt] = record.lastAttemptAt
            },
        ) {
            it[username] = record.username
            it[attempts] = record.attempts
            it[lockedUntil] = record.lockedUntil
            it[lastAttemptAt] = record.lastAttemptAt
        }
    }

    suspend fun delete(username: String): Unit = dbQuery {
        AccountLockoutTable.deleteWhere {
            AccountLockoutTable.username eq username
        }
    }

    suspend fun deleteExpired(now: Instant): Unit = dbQuery {
        AccountLockoutTable.deleteWhere {
            (AccountLockoutTable.lockedUntil.isNotNull()) and
                (AccountLockoutTable.lockedUntil less now)
        }
    }

    private fun ResultRow.toLockoutRecord() = LockoutRecord(
        username = this[AccountLockoutTable.username],
        attempts = this[AccountLockoutTable.attempts],
        lockedUntil = this[AccountLockoutTable.lockedUntil],
        lastAttemptAt = this[AccountLockoutTable.lastAttemptAt],
    )
}
