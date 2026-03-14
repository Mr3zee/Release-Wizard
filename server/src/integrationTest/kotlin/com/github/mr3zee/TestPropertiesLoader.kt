package com.github.mr3zee

import java.io.File
import java.util.Properties

/**
 * Shared utility for locating and loading `local.properties` from the project root.
 * Walks up from the current working directory to find the file.
 */
object TestPropertiesLoader {
    fun findLocalProperties(): File {
        val cwd = File("local.properties")
        if (cwd.exists()) return cwd
        var dir = File(".").absoluteFile.parentFile
        while (dir != null) {
            val candidate = File(dir, "local.properties")
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        return cwd
    }

    fun loadProperties(): Properties? {
        val file = findLocalProperties()
        if (!file.exists()) return null
        return Properties().apply { file.inputStream().use { load(it) } }
    }
}
