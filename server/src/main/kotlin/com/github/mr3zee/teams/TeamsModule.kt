package com.github.mr3zee.teams

import com.github.mr3zee.audit.AuditRepository
import com.github.mr3zee.audit.AuditService
import com.github.mr3zee.audit.ExposedAuditRepository
import org.koin.dsl.module

val teamsModule = module {
    single<TeamRepository> { ExposedTeamRepository(get()) }
    single { TeamAccessService(get()) }
    single<TeamService> { DefaultTeamService(get(), get(), get(), get()) }
    single<AuditRepository> { ExposedAuditRepository(get()) }
    single { AuditService(get(), get()) }
}
