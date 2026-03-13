package com.github.mr3zee.releases

import com.github.mr3zee.execution.BlockExecutor
import com.github.mr3zee.execution.DispatchingBlockExecutor
import com.github.mr3zee.execution.ExecutionEngine
import com.github.mr3zee.execution.StubBlockExecutor
import com.github.mr3zee.execution.executors.GitHubPublicationExecutor
import com.github.mr3zee.execution.executors.SlackMessageExecutor
import com.github.mr3zee.model.BlockType
import org.koin.dsl.module

val releasesModule = module {
    single<ReleasesRepository> { ExposedReleasesRepository(get()) }
    single<BlockExecutor> {
        val stub = StubBlockExecutor()
        val httpClient = get<io.ktor.client.HttpClient>()
        DispatchingBlockExecutor(
            mapOf(
                BlockType.TEAMCITY_BUILD to stub,
                BlockType.GITHUB_ACTION to stub,
                BlockType.GITHUB_PUBLICATION to GitHubPublicationExecutor(httpClient),
                BlockType.MAVEN_CENTRAL_PUBLICATION to stub,
                BlockType.SLACK_MESSAGE to SlackMessageExecutor(httpClient),
            )
        )
    }
    single { ExecutionEngine(get(), get(), get()) }
    single<ReleasesService> { DefaultReleasesService(get(), get(), get()) }
}
