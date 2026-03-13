package com.github.mr3zee.notifications

import org.koin.dsl.module

val notificationsModule = module {
    single<NotificationRepository> { ExposedNotificationRepository(get()) }
    single<NotificationService> { DefaultNotificationService(get(), get()) }
    single { NotificationListener(get(), get(), get()) }
}
