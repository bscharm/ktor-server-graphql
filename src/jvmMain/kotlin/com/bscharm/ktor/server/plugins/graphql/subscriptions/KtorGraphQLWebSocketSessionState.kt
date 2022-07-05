package com.bscharm.ktor.server.plugins.graphql.subscriptions

import com.bscharm.ktor.server.plugins.graphql.subscriptions.GraphQLWebSocketMessage.SubscriptionMessage.SubscriptionMessagePayload
import io.ktor.util.collections.ConcurrentSet
import io.ktor.websocket.WebSocketSession
import java.util.concurrent.ConcurrentHashMap

internal class KtorGraphQLWebSocketSessionState {
    private val sessionInitializationState = ConcurrentSet<WebSocketSession>()
    private val sessionOperationState =
        ConcurrentHashMap<WebSocketSession, ConcurrentHashMap<String, SubscriptionMessagePayload>>()

    fun initialize(session: WebSocketSession) {
        sessionInitializationState.add(session)
    }

    fun isInitialized(session: WebSocketSession): Boolean {
        return sessionInitializationState.contains(session)
    }

    fun operationSubscribed(id: String, session: WebSocketSession): Boolean {
        return sessionOperationState[session]?.contains(key = id) ?: false
    }

    fun markOperationSubscribed(id: String, session: WebSocketSession, payload: SubscriptionMessagePayload) {
        sessionOperationState[session]?.put(id, payload)
    }
}
