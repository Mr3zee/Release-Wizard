package com.github.mr3zee.teams

import org.koin.dsl.module

val teamsModule = module {
    single<TeamRepository> { ExposedTeamRepository(get()) }
    single { TeamAccessService(get()) }
    single<TeamService> { DefaultTeamService(get(), get(), get(), get()) }
}
