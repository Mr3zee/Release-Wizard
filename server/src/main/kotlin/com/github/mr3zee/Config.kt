package com.github.mr3zee

import io.ktor.server.config.*

data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
    val driver: String,
)

fun ApplicationConfig.databaseConfig(): DatabaseConfig {
    return DatabaseConfig(
        url = property("app.database.url").getString(),
        user = property("app.database.user").getString(),
        password = property("app.database.password").getString(),
        driver = property("app.database.driver").getString(),
    )
}
