package com.github.mr3zee

import com.github.mr3zee.persistence.dataSource
import com.github.mr3zee.persistence.initDatabase
import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module
import javax.sql.DataSource

val appModule = module {
    single { loadConfig() }
    single<DataSource> { dataSource(get<Config>().database) }
    single<Database> { initDatabase(get<DataSource>()) }
}
