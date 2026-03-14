package com.github.mr3zee.persistence

import org.jetbrains.exposed.v1.core.Table

object ReleaseTagTable : Table("release_tags") {
    val releaseId = varchar("release_id", 36)
    val tag = varchar("tag", 255)

    override val primaryKey = PrimaryKey(releaseId, tag)

    init {
        index(false, tag)
    }
}
