package com.github.mr3zee.webhooks

import org.koin.dsl.module

val webhooksModule = module {
    single<PendingWebhookRepository> { ExposedPendingWebhookRepository(get()) }
    single { WebhookService(get(), get()) }
    single<StatusWebhookTokenRepository> { ExposedStatusWebhookTokenRepository(get()) }
    single { StatusWebhookService(get(), get(), get(), get()) }
}
