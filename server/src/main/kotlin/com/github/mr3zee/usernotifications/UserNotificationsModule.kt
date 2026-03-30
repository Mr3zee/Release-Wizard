package com.github.mr3zee.usernotifications

import org.koin.dsl.module

val userNotificationsModule = module {
    single<UserNotificationRepository> { ExposedUserNotificationRepository(get()) }
    single<UserNotificationService> { DefaultUserNotificationService(get()) }
    single { UserNotificationGenerator(get(), get(), get(), get()) }
}
