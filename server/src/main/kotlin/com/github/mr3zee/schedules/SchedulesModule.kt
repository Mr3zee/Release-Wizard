package com.github.mr3zee.schedules

import org.koin.dsl.module

val schedulesModule = module {
    single<ScheduleRepository> { ExposedScheduleRepository(get()) }
    single { DefaultScheduleService(get(), get(), get()) as ScheduleService }
    single { SchedulerService(get(), get()) }
}
