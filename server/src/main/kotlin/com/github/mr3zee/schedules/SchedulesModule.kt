package com.github.mr3zee.schedules

import org.koin.dsl.module

val schedulesModule = module {
    single<ScheduleRepository> { ExposedScheduleRepository(get()) }
    single<ScheduleService> { DefaultScheduleService(get(), get(), get()) }
    single { SchedulerService(get(), get()) }
}
