package com.github.mr3zee.connections

import org.koin.dsl.module

val connectionsModule = module {
    single<ConnectionsRepository> { ExposedConnectionsRepository(get(), get()) }
    single<ConnectionsService> { DefaultConnectionsService(get(), get()) }
    single { ConnectionTester(get()) }
}
