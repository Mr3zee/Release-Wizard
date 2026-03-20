package com.github.mr3zee.auth

import org.koin.dsl.module

val authModule = module {
    single<AuthService> { DatabaseAuthService(get()) }
    single { PasswordValidator(get()) }
    single { AccountLockoutRepository(get()) }
    single { AccountLockoutService(get()) }
}
