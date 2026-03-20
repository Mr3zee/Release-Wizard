package com.github.mr3zee.auth

import org.koin.dsl.module

val authModule = module {
    single<AuthService> { DatabaseAuthService(get()) }
    single { PasswordValidator(get()) }
    // AUTH-H4: Per-username account lockout with exponential backoff
    single { AccountLockoutService() }
}
