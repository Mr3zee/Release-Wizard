package com.github.mr3zee.projects

import org.koin.dsl.module

val projectsModule = module {
    single<ProjectsRepository> { ExposedProjectsRepository(get()) }
    single<ProjectsService> { DefaultProjectsService(get(), get(), get(), get(), get()) }
}
