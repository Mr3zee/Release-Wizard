package com.github.mr3zee.releases

import com.github.mr3zee.execution.BlockExecutor
import com.github.mr3zee.execution.ExecutionEngine
import com.github.mr3zee.execution.StubBlockExecutor
import org.koin.dsl.module

val releasesModule = module {
    single<ReleasesRepository> { ExposedReleasesRepository(get()) }
    single<BlockExecutor> { StubBlockExecutor() }
    single { ExecutionEngine(get(), get()) }
    single<ReleasesService> { DefaultReleasesService(get(), get(), get()) }
}
