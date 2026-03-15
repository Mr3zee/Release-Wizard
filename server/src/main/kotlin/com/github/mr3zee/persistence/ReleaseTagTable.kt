package com.github.mr3zee.persistence

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object ReleaseTagTable : Table("release_tags") {
    val releaseId = reference("release_id", ReleaseTable, onDelete = ReferenceOption.CASCADE)
    val tag = varchar("tag", 255)
    val teamId = reference("team_id", TeamTable, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(releaseId, tag)

    init {
        index(false, tag)
        index(false, teamId)
    }
}
