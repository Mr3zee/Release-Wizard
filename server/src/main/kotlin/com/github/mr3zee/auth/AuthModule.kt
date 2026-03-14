package com.github.mr3zee.auth

import com.github.mr3zee.PasswordPolicyConfig
import org.koin.dsl.module

val authModule = module {
    single<AuthService> { DatabaseAuthService(get()) }
    single { PasswordValidator(get()) }
}
