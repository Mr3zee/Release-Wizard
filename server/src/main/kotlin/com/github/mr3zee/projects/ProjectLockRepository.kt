package com.github.mr3zee.projects

import com.github.mr3zee.api.ProjectLockInfo

interface ProjectLockRepository {
    suspend fun tryAcquire(projectId: String, userId: String, username: String, ttlMinutes: Long): ProjectLockInfo?
    suspend fun release(projectId: String, userId: String): Boolean
    suspend fun forceRelease(projectId: String): Boolean
    suspend fun heartbeat(projectId: String, userId: String, ttlMinutes: Long): ProjectLockInfo?
    suspend fun findActiveLock(projectId: String): ProjectLockInfo?
}
