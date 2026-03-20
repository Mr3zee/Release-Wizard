package com.github.mr3zee.mavenpublication

import org.koin.dsl.module

val mavenTriggerModule = module {
    single<MavenTriggerRepository> { ExposedMavenTriggerRepository(get()) }
    single { MavenMetadataFetcher(get()) }
    single<MavenTriggerService> { DefaultMavenTriggerService(get(), get(), get(), get(), get()) }
    single { MavenPollerService(get(), get(), get()) }
}
