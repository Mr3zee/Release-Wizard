package com.github.mr3zee.api

import com.github.mr3zee.AppJson
import com.github.mr3zee.model.BlockId
import com.github.mr3zee.model.ReleaseId
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ReleaseApiClient(private val client: HttpClient) {

    suspend fun listReleases(): ReleaseListResponse {
        return client.get(serverUrl(ApiRoutes.Releases.BASE)).body()
    }

    // todo claude: unused
    suspend fun getRelease(id: ReleaseId): ReleaseResponse {
        return client.get(serverUrl(ApiRoutes.Releases.byId(id.value))).body()
    }

    suspend fun startRelease(request: CreateReleaseRequest): ReleaseResponse {
        return client.post(serverUrl(ApiRoutes.Releases.BASE)) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun cancelRelease(id: ReleaseId): ReleaseResponse {
        return client.post(serverUrl(ApiRoutes.Releases.cancel(id.value))).body()
    }

    suspend fun approveBlock(releaseId: ReleaseId, blockId: BlockId, input: Map<String, String> = emptyMap()): Boolean {
        val response = client.post(serverUrl(ApiRoutes.Releases.approveBlock(releaseId.value, blockId.value))) {
            contentType(ContentType.Application.Json)
            setBody(ApproveBlockRequest(input = input))
        }
        return response.status == HttpStatusCode.OK
    }

    fun watchRelease(releaseId: ReleaseId): Flow<ReleaseEvent> = flow {
        client.webSocket(wsServerUrl(ApiRoutes.Releases.ws(releaseId.value))) {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val event = AppJson.decodeFromString(ReleaseEvent.serializer(), frame.readText())
                    emit(event)
                    if (event is ReleaseEvent.ReleaseCompleted) break
                }
            }
        }
    }
}
