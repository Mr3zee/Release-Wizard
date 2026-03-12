package com.github.mr3zee

data class Config(
    val database: DatabaseConfig = DatabaseConfig(),
    val server: ServerConfig = ServerConfig(),
)

data class DatabaseConfig(
    val url: String = "jdbc:postgresql://localhost:5432/release_wizard",
    val user: String = "postgres",
    val password: String = "postgres",
    val driver: String = "org.postgresql.Driver",
)

data class ServerConfig(
    val host: String = "0.0.0.0",
    val port: Int = SERVER_PORT,
)

fun loadConfig(): Config {
    return Config(
        database = DatabaseConfig(
            url = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/release_wizard",
            user = System.getenv("DB_USER") ?: "postgres",
            password = System.getenv("DB_PASSWORD") ?: "postgres",
            driver = System.getenv("DB_DRIVER") ?: "org.postgresql.Driver",
        ),
        server = ServerConfig(
            host = System.getenv("SERVER_HOST") ?: "0.0.0.0",
            port = System.getenv("SERVER_PORT")?.toIntOrNull() ?: SERVER_PORT,
        ),
    )
}
