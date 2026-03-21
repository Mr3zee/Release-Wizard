package com.github.mr3zee.auth

import com.github.mr3zee.AuthConfig
import org.koin.dsl.module

val authModule = module {
    single<AuthService> {
        val authConfig = get<AuthConfig>()
        DatabaseAuthService(get(), authConfig.pepperSecret, authConfig.pepperSecretOld)
    }
    single<PasswordResetService> { DatabasePasswordResetService(get()) }
    single { PasswordValidator(get()) }
    single { AccountLockoutRepository(get()) }
    single { AccountLockoutService(get()) }
    single<OAuthService> { DatabaseOAuthService(get()) }
}
