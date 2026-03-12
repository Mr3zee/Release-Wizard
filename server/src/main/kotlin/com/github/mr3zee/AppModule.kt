package com.github.mr3zee

import com.github.mr3zee.persistence.dataSource
import com.github.mr3zee.persistence.initDatabase
import com.github.mr3zee.security.EncryptionService
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.dsl.module
import javax.sql.DataSource

fun appModule(dbConfig: DatabaseConfig, encryptionConfig: EncryptionConfig, authConfig: AuthConfig) = module {
    single { dbConfig }
    single { encryptionConfig }
    single { authConfig }
    single<DataSource> { dataSource(get()) }
    single<Database> { initDatabase(get()) }
    single { EncryptionService(get()) }
}
