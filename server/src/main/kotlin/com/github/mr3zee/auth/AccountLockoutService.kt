package com.github.mr3zee.auth

import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class AccountLockoutService(
    private val repository: AccountLockoutRepository,
) {
    private val log = LoggerFactory.getLogger(AccountLockoutService::class.java)

    suspend fun checkLocked(username: String): Duration? {
        val record = repository.find(username) ?: return null
        val lockedUntil = record.lockedUntil ?: return null
        val remaining = lockedUntil - Clock.System.now()
        return if (remaining.isPositive()) remaining else null
    }

    suspend fun recordFailure(username: String) {
        val now = Clock.System.now()
        val existing = repository.find(username)
        val attempts = ((existing?.attempts ?: 0) + 1).coerceAtMost(MAX_TRACKED_ATTEMPTS)

        val lockedUntil = if (attempts >= MAX_ATTEMPTS) {
            val lockoutRound = (attempts - MAX_ATTEMPTS).coerceAtMost(MAX_BACKOFF_EXPONENT)
            val lockoutDuration = (INITIAL_LOCKOUT * (1 shl lockoutRound)).coerceAtMost(MAX_LOCKOUT)
            log.warn("Account '{}' locked for {} after {} failed attempts", username, lockoutDuration, attempts)
            now + lockoutDuration
        } else {
            null
        }

        repository.upsert(
            LockoutRecord(
                username = username,
                attempts = attempts,
                lockedUntil = lockedUntil,
                lastAttemptAt = now,
            )
        )
    }

    suspend fun recordSuccess(username: String) {
        repository.delete(username)
    }

    companion object {
        const val MAX_ATTEMPTS = 5
        val INITIAL_LOCKOUT: Duration = 15.seconds
        val MAX_LOCKOUT: Duration = 15.minutes
        const val MAX_BACKOFF_EXPONENT = 6
        const val MAX_TRACKED_ATTEMPTS = MAX_ATTEMPTS + MAX_BACKOFF_EXPONENT + 1
    }
}
