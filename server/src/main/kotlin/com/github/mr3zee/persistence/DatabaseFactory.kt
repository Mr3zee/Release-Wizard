package com.github.mr3zee.persistence

import com.github.mr3zee.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import javax.sql.DataSource

fun dataSource(config: DatabaseConfig): DataSource {
    val hikariConfig = HikariConfig().apply {
        jdbcUrl = config.url
        username = config.user
        password = config.password
        driverClassName = config.driver
        maximumPoolSize = 10
        validate()
    }
    return HikariDataSource(hikariConfig)
}

fun initDatabase(ds: DataSource): Database {
    val database = Database.connect(ds)
    transaction(database) {
        SchemaUtils.create(ProjectTemplateTable)
    }
    return database
}
