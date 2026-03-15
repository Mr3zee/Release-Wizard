package com.github.mr3zee.projects

import org.koin.dsl.module

val projectLockModule = module {
    single<ProjectLockRepository> { ExposedProjectLockRepository(get()) }
    single<ProjectLockService> { DefaultProjectLockService(get(), get(), get(), get()) }
}
