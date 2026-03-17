package com.github.mr3zee.releases

import com.github.mr3zee.execution.BlockExecutor
import com.github.mr3zee.execution.DispatchingBlockExecutor
import com.github.mr3zee.execution.ExecutionEngine
import com.github.mr3zee.execution.RecoveryService
import com.github.mr3zee.execution.executors.*
import com.github.mr3zee.model.BlockType
import com.github.mr3zee.webhooks.StatusWebhookService
import io.ktor.client.HttpClient
import org.koin.dsl.module

val releasesModule = module {
    single<ReleasesRepository> { ExposedReleasesRepository(get()) }
    single { TeamCityArtifactService(get<HttpClient>()) }
    single<BlockExecutor> {
        val httpClient = get<HttpClient>()
        DispatchingBlockExecutor(
            mapOf(
                BlockType.TEAMCITY_BUILD to TeamCityBuildExecutor(httpClient, get(), get(), get(), get<StatusWebhookService>()),
                BlockType.GITHUB_ACTION to GitHubActionExecutor(httpClient, get(), get()),
                BlockType.GITHUB_PUBLICATION to GitHubPublicationExecutor(httpClient),
                BlockType.SLACK_MESSAGE to SlackMessageExecutor(httpClient),
            )
        )
    }
    single { ExecutionEngine(get(), get(), get(), get()) }
    single { RecoveryService(get(), get(), get(), get()) }
    single<ReleasesService> { DefaultReleasesService(get(), get(), get(), get(), get(), get(), get()) }
}
