package com.github.mr3zee

import com.github.mr3zee.persistence.dataSource
import com.github.mr3zee.persistence.initDatabase
import com.github.mr3zee.security.EncryptionService
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.dsl.module
import javax.sql.DataSource

fun appModule(
    dbConfig: DatabaseConfig,
    encryptionConfig: EncryptionConfig,
    authConfig: AuthConfig,
    webhookConfig: WebhookConfig,
    passwordPolicyConfig: PasswordPolicyConfig = PasswordPolicyConfig(),
) = module {
    single { dbConfig }
    single { encryptionConfig }
    single { authConfig }
    single { webhookConfig }
    single { passwordPolicyConfig }
    single<DataSource> { dataSource(get()) }
    single<Database> { initDatabase(get()) }
    single { EncryptionService(get()) }
    single {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(AppJson)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 30_000
            }
        }
    }
}
