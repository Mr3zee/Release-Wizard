package com.github.mr3zee.auth

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * AUTH-H4: Per-username account lockout with exponential backoff.
 *
 * After [MAX_ATTEMPTS] consecutive failed login attempts for a username, the account
 * is locked for an exponentially increasing duration (15s → 30s → 60s → ... → 15min cap).
 * A successful login resets the counter.
 *
 * In-memory only — lockout state does not survive restarts.
 */
class AccountLockoutService {
    private val log = LoggerFactory.getLogger(AccountLockoutService::class.java)

    private data class LockoutState(
        val failedAttempts: Int,
        val lockUntil: Instant?,
    )

    private val states = ConcurrentHashMap<String, LockoutState>()

    /**
     * Returns the remaining lockout duration if the account is locked, or null if login may proceed.
     */
    fun checkLocked(username: String): Duration? {
        val state = states[username] ?: return null
        val lockUntil = state.lockUntil ?: return null
        val remaining = lockUntil - Clock.System.now()
        return if (remaining.isPositive()) remaining else null
    }

    /**
     * Records a failed login attempt. If the threshold is reached, computes a lockout duration.
     */
    fun recordFailure(username: String) {
        // Periodic eviction of stale entries to prevent unbounded memory growth
        evictStaleEntries()

        states.compute(username) { _, current ->
            val attempts = ((current?.failedAttempts ?: 0) + 1).coerceAtMost(MAX_TRACKED_ATTEMPTS)
            if (attempts >= MAX_ATTEMPTS) {
                val lockoutRound = (attempts - MAX_ATTEMPTS).coerceAtMost(MAX_BACKOFF_EXPONENT)
                val lockoutDuration = (INITIAL_LOCKOUT * (1 shl lockoutRound))
                    .coerceAtMost(MAX_LOCKOUT)
                log.warn("Account '{}' locked for {} after {} failed attempts", username, lockoutDuration, attempts)
                LockoutState(failedAttempts = attempts, lockUntil = Clock.System.now() + lockoutDuration)
            } else {
                LockoutState(failedAttempts = attempts, lockUntil = null)
            }
        }
    }

    /** Remove entries whose lockout has expired and are below the threshold, preventing unbounded memory growth. */
    private fun evictStaleEntries() {
        if (states.size <= MAX_MAP_SIZE) return
        val now = Clock.System.now()
        states.entries.removeIf { (_, state) ->
            val lockUntil = state.lockUntil
            lockUntil != null && now > lockUntil
        }
    }

    /**
     * Clears lockout state on successful login.
     */
    fun recordSuccess(username: String) {
        states.remove(username)
    }

    companion object {
        const val MAX_ATTEMPTS = 5
        val INITIAL_LOCKOUT: Duration = 15.seconds
        val MAX_LOCKOUT: Duration = 15.minutes
        const val MAX_BACKOFF_EXPONENT = 6 // 15s * 2^6 = 960s = 16min (capped to 15min)
        const val MAX_TRACKED_ATTEMPTS = MAX_ATTEMPTS + MAX_BACKOFF_EXPONENT + 1
        const val MAX_MAP_SIZE = 10_000
    }
}
