package com.bscharm.ktor.server.plugins.graphql.subscriptions

import io.ktor.util.collections.ConcurrentSet
import kotlinx.coroutines.Job
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class KtorGraphQLWebSocketSessionState {
    private val sessionInitializationState = ConcurrentSet<UUID>()
    private val sessionOperationState =
        ConcurrentHashMap<UUID, ConcurrentHashMap<String, Job>>()

    fun initialize(sessionId: UUID) {
        sessionInitializationState.add(sessionId)
    }

    fun isInitialized(sessionId: UUID): Boolean {
        return sessionInitializationState.contains(sessionId)
    }

    fun operationSubscribed(operationId: String, sessionId: UUID): Boolean {
        return sessionOperationState.getOrPut(sessionId) { ConcurrentHashMap() }.contains(operationId)
    }

    fun markOperationSubscribed(operationId: String, sessionId: UUID, operationJob: Job) {
        sessionOperationState.getOrPut(sessionId) { ConcurrentHashMap() }[operationId] = operationJob
    }

    fun cancelOperation(operationId: String, sessionId: UUID) {
        val operationJob = sessionOperationState[sessionId]?.get(operationId)
        operationJob?.cancel()
        sessionOperationState[sessionId]?.remove(operationId)
    }
}
