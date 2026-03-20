package com.github.mr3zee.triggers

import com.github.mr3zee.WebhookConfig
import org.koin.dsl.module

val triggersModule = module {
    single<TriggerRepository> { ExposedTriggerRepository(get()) }
    single<TriggerService> {
        val webhookConfig = get<WebhookConfig>()
        DefaultTriggerService(get(), get(), get(), webhookConfig.baseUrl, get(), get())
    }
}
