package com.github.mr3zee.projects

import com.github.mr3zee.api.ProjectLockInfo

class LockConflictException(val lock: ProjectLockInfo) : RuntimeException("Project is locked by ${lock.username}")
