package com.bscharm.ktor.server.plugins.graphql.subscriptions

import io.ktor.util.collections.ConcurrentSet
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.Job
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

class KtorGraphQLWebSocketSessionState {
    private val sessionInitializationState = ConcurrentSet<WebSocketSession>()
    private val sessionOperationState =
        ConcurrentHashMap<WebSocketSession, ConcurrentHashMap<String, CoroutineContext>>()

    fun initialize(session: WebSocketSession) {
        sessionInitializationState.add(session)
    }

    fun isInitialized(session: WebSocketSession): Boolean {
        return sessionInitializationState.contains(session)
    }

    fun operationSubscribed(id: String, session: WebSocketSession): Boolean {
        return sessionOperationState.getOrPut(session) { ConcurrentHashMap() }.contains(id)
    }

    fun markOperationSubscribed(id: String, session: WebSocketSession, context: CoroutineContext) {
        sessionOperationState.getOrPut(session) { ConcurrentHashMap() }[id] = context
    }

    fun cancelOperation(id: String, session: WebSocketSession) {
        val coroutineContext = sessionOperationState[session]?.get(id)
        coroutineContext?.get(Job)?.cancel()
        sessionOperationState[session]?.remove(id)
    }
}
