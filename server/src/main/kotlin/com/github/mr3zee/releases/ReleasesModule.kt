package com.github.mr3zee.releases

import com.github.mr3zee.execution.BlockExecutor
import com.github.mr3zee.execution.DispatchingBlockExecutor
import com.github.mr3zee.execution.ExecutionEngine
import com.github.mr3zee.execution.StubBlockExecutor
import com.github.mr3zee.model.BlockType
import org.koin.dsl.module

val releasesModule = module {
    single<ReleasesRepository> { ExposedReleasesRepository(get()) }
    single<BlockExecutor> {
        val stub = StubBlockExecutor()
        DispatchingBlockExecutor(
            mapOf(
                BlockType.TEAMCITY_BUILD to stub,
                BlockType.GITHUB_ACTION to stub,
                BlockType.GITHUB_PUBLICATION to stub,
                BlockType.MAVEN_CENTRAL_PUBLICATION to stub,
                BlockType.SLACK_MESSAGE to stub,
            )
        )
    }
    single { ExecutionEngine(get(), get(), get()) }
    single<ReleasesService> { DefaultReleasesService(get(), get(), get()) }
}
