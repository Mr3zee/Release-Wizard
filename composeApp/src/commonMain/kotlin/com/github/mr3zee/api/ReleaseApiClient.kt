package com.github.mr3zee.api

import com.github.mr3zee.AppJson
import com.github.mr3zee.model.BlockId
import com.github.mr3zee.model.ReleaseId
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.request.parameter
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ReleaseApiClient(private val client: HttpClient) {

    suspend fun listReleases(
        offset: Int = 0,
        limit: Int = 20,
        search: String? = null,
        status: String? = null,
        projectId: String? = null,
    ): ReleaseListResponse {
        return client.get(serverUrl(ApiRoutes.Releases.BASE)) {
            parameter("offset", offset)
            parameter("limit", limit)
            search?.let { parameter("q", it) }
            status?.let { parameter("status", it) }
            projectId?.let { parameter("projectId", it) }
        }.body()
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

    suspend fun rerunRelease(id: ReleaseId): ReleaseResponse {
        return client.post(serverUrl(ApiRoutes.Releases.rerun(id.value))).body()
    }

    suspend fun stopRelease(id: ReleaseId): ReleaseResponse {
        return client.post(serverUrl(ApiRoutes.Releases.stop(id.value))).body()
    }

    suspend fun resumeRelease(id: ReleaseId): ReleaseResponse {
        return client.post(serverUrl(ApiRoutes.Releases.resume(id.value))).body()
    }

    suspend fun stopBlock(releaseId: ReleaseId, blockId: BlockId): ReleaseResponse {
        return client.post(serverUrl(ApiRoutes.Releases.stopBlock(releaseId.value, blockId.value))).body()
    }

    suspend fun archiveRelease(id: ReleaseId): ReleaseResponse {
        return client.post(serverUrl(ApiRoutes.Releases.archive(id.value))).body()
    }

    suspend fun deleteRelease(id: ReleaseId) {
        client.delete(serverUrl(ApiRoutes.Releases.byId(id.value)))
    }

    suspend fun approveBlock(releaseId: ReleaseId, blockId: BlockId, input: Map<String, String> = emptyMap()): Boolean {
        val response = client.post(serverUrl(ApiRoutes.Releases.approveBlock(releaseId.value, blockId.value))) {
            contentType(ContentType.Application.Json)
            setBody(ApproveBlockRequest(input = input))
        }
        return response.status == HttpStatusCode.OK
    }

    fun watchRelease(releaseId: ReleaseId, lastSeq: Long? = null): Flow<ReleaseEvent> = flow {
        val wsUrl = buildString {
            append(wsServerUrl(ApiRoutes.Releases.ws(releaseId.value)))
            if (lastSeq != null && lastSeq > 0) {
                append("?lastSeq=$lastSeq")
            }
        }
        client.webSocket(wsUrl) {
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
