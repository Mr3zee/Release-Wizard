package com.github.mr3zee.webhooks

import com.github.mr3zee.execution.executors.BuildPollingService
import org.koin.dsl.module

val webhooksModule = module {
    single { BuildPollingService(get()) }
    single<StatusWebhookTokenRepository> { ExposedStatusWebhookTokenRepository(get()) }
    single { StatusWebhookService(get(), get(), lazy { get() }, get()) }
}
