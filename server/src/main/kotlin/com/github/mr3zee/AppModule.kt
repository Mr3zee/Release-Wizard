package com.github.mr3zee

import com.github.mr3zee.persistence.dataSource
import com.github.mr3zee.persistence.initDatabase
import com.github.mr3zee.plugins.SsrfProtection
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
            // EXEC-H6: Explicitly configure TLS verification (CIO uses JVM default truststore).
            // This ensures certificate validation is active and cannot be accidentally bypassed.
            engine {
                https {
                    trustManager = null // null = use JVM default trust manager (verifies certificates)
                }
            }
            // CONN-C1: SSRF protection at HTTP client level — validates every outgoing request
            install(SsrfProtection)
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
