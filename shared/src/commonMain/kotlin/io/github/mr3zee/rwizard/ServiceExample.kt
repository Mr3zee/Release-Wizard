package io.github.mr3zee.rwizard

import kotlinx.coroutines.flow.Flow
import kotlinx.rpc.annotations.Rpc

@Rpc
interface ServiceExample {
    suspend fun unaryCall(name: String, lastName: String): String
    fun streamingCall(): Flow<String>
}
