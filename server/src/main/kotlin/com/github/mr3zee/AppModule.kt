package com.github.mr3zee

import com.github.mr3zee.persistence.dataSource
import com.github.mr3zee.persistence.initDatabase
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.dsl.module
import javax.sql.DataSource

fun appModule(dbConfig: DatabaseConfig) = module {
    single { dbConfig }
    single<DataSource> { dataSource(get()) }
    single<Database> { initDatabase(get()) }
}
