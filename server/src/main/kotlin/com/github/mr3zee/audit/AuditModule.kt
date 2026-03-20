package com.github.mr3zee.audit

import org.koin.dsl.module

/**
 * TAG-M4: Dedicated Koin module for audit services.
 * Previously registered in teamsModule, which was a scope leak since
 * audit is a cross-cutting concern used by all modules.
 */
val auditModule = module {
    single<AuditRepository> { ExposedAuditRepository(get()) }
    single { AuditService(get(), get()) }
}
