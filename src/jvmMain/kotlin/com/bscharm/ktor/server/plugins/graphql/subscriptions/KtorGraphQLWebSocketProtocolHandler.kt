package com.bscharm.ktor.server.plugins.graphql.subscriptions

import com.expediagroup.graphql.server.extensions.toGraphQLError
import com.expediagroup.graphql.server.types.GraphQLRequest
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.reactive.asFlow
import org.slf4j.LoggerFactory

class KtorGraphQLWebSocketProtocolHandler(private val subscriptionHandler: KtorGraphQLSubscriptionHandler) {
    private val logger = LoggerFactory.getLogger(KtorGraphQLWebSocketProtocolHandler::class.java)
    private val mapper = jacksonObjectMapper()
    private val state = KtorGraphQLWebSocketSessionState()

    fun initReceived(session: WebSocketSession): Boolean {
        return state.isInitialized(session)
    }

    suspend fun handle(
        message: GraphQLWebSocketMessage,
        session: WebSocketServerSession
    ): Flow<GraphQLWebSocketMessage> = when (message) {
        is GraphQLWebSocketMessage.PingMessage -> onPing()
        is GraphQLWebSocketMessage.ConnectionInitMessage -> onConnectionInit(session)
        is GraphQLWebSocketMessage.SubscriptionMessage -> onSubscribe(message, session)
        else -> emptyFlow()
    }

    private suspend fun onConnectionInit(session: WebSocketServerSession): Flow<GraphQLWebSocketMessage.ConnectionAckMessage> {
        if (state.isInitialized(session)) {
            session.close(CloseReason(4429, "Too many initialization requests."))
            return emptyFlow()
        } else {
            state.initialize(session)
        }

        val response = GraphQLWebSocketMessage.ConnectionAckMessage(payload = emptyMap())
        return flowOf(response)
    }

    private suspend fun onSubscribe(
        message: GraphQLWebSocketMessage.SubscriptionMessage,
        session: WebSocketServerSession
    ): Flow<GraphQLWebSocketMessage> {
        if (!state.isInitialized(session)) {
            session.close(CloseReason(4401, "Unauthorized."))
            return emptyFlow()
        }

        if (state.operationSubscribed(message.id, session)) {
            session.close(CloseReason(4409, "Subscriber for ${message.id} already exists."))
        }

        state.markOperationSubscribed(message.id, session, message.payload)
        val graphQLRequest = mapper.convertValue<GraphQLRequest>(message.payload)
        return subscriptionHandler.execute(graphQLRequest)
            .asFlow()
            .map {
                if (it.errors.isEmpty()) {
                    GraphQLWebSocketMessage.NextMessage(message.id, it)
                } else {
                    GraphQLWebSocketMessage.ErrorMessage(message.id, it.errors)
                }
            }
            .catch { throwable ->
                val error = throwable.toGraphQLError()
                emit(GraphQLWebSocketMessage.ErrorMessage(id = message.id, payload = listOf(error)))
            }
            .onCompletion { emit(GraphQLWebSocketMessage.CompleteMessage(id = message.id)) }
    }

    private fun onPing(): Flow<GraphQLWebSocketMessage.PongMessage> {
        val response = GraphQLWebSocketMessage.PongMessage(payload = emptyMap())
        return flowOf(response)
    }
}
