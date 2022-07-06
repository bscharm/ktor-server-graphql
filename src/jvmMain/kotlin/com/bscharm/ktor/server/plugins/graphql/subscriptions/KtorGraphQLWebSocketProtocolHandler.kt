package com.bscharm.ktor.server.plugins.graphql.subscriptions

import com.expediagroup.graphql.server.extensions.toGraphQLError
import com.expediagroup.graphql.server.types.GraphQLRequest
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import org.slf4j.LoggerFactory
import kotlin.coroutines.CoroutineContext

class KtorGraphQLWebSocketProtocolHandler(private val subscriptionHandler: KtorGraphQLSubscriptionHandler) {
    private val logger = LoggerFactory.getLogger("KtorGraphQLPlugin")
    private val mapper = jacksonObjectMapper()
    private val state = KtorGraphQLWebSocketSessionState()

    fun initReceived(session: WebSocketSession): Boolean {
        return state.isInitialized(session)
    }

    suspend fun handle(
        frame: Frame,
        session: WebSocketServerSession,
        coroutineContext: CoroutineContext
    ): Flow<GraphQLWebSocketMessage> {
        return when (val message = graphQLWebSocketMessage(frame)) {
            is GraphQLWebSocketMessage.PingMessage -> onPing()
            is GraphQLWebSocketMessage.PongMessage -> emptyFlow()
            is GraphQLWebSocketMessage.ConnectionInitMessage -> onConnectionInit(session)
            is GraphQLWebSocketMessage.SubscriptionMessage -> onSubscribe(message, session, coroutineContext)
            is GraphQLWebSocketMessage.CompleteMessage -> onComplete(message, session)
            else -> {
                session.close(CloseReason(4400, "Unrecognized message"))
                emptyFlow()
            }
        }
    }

    private fun graphQLWebSocketMessage(frame: Frame): GraphQLWebSocketMessage? = kotlin.runCatching {
        return mapper.readValue<GraphQLWebSocketMessage>(frame.data)
    }.getOrNull()

    private fun onComplete(
        message: GraphQLWebSocketMessage.CompleteMessage,
        session: WebSocketSession
    ): Flow<GraphQLWebSocketMessage> {
        logger.debug("received complete")
        state.cancelOperation(message.id, session)
        logger.debug("operation ${message.id} cancelled")
        return emptyFlow()
    }

    private suspend fun onConnectionInit(session: WebSocketServerSession): Flow<GraphQLWebSocketMessage.ConnectionAckMessage> {
        logger.debug("received init")

        if (state.isInitialized(session)) {
            logger.debug("closing session: received duplicate init")
            session.close(CloseReason(4429, "Too many initialization requests."))
            return emptyFlow()
        } else {
            state.initialize(session)
            logger.debug("session initialized")
        }

        val response = GraphQLWebSocketMessage.ConnectionAckMessage(payload = emptyMap())
        return flowOf(response)
    }

    private suspend fun onSubscribe(
        message: GraphQLWebSocketMessage.SubscriptionMessage,
        session: WebSocketServerSession,
        coroutineContext: CoroutineContext
    ): Flow<GraphQLWebSocketMessage> {
        logger.debug("received subscribe")

        if (!state.isInitialized(session)) {
            logger.debug("closing session: received subscribe before init")
            session.close(CloseReason(4401, "Unauthorized."))
            return emptyFlow()
        }

        if (state.operationSubscribed(message.id, session)) {
            logger.debug("closing session: received duplicate subscribe for operation ${message.id}")
            session.close(CloseReason(4409, "Subscriber for ${message.id} already exists."))
        }

        state.markOperationSubscribed(message.id, session, coroutineContext)
        logger.debug("session subscribed to operation ${message.id}")
        val graphQLRequest = mapper.convertValue<GraphQLRequest>(message.payload)

        return subscriptionHandler.execute(graphQLRequest)
            .map {
                if (it.errors.isEmpty()) {
                    GraphQLWebSocketMessage.NextMessage(message.id, it)
                } else {
                    GraphQLWebSocketMessage.ErrorMessage(message.id, it.errors)
                }
            }
            .catch { throwable ->
                logger.debug("exception while executing graphql request")
                val error = throwable.toGraphQLError()
                emit(GraphQLWebSocketMessage.ErrorMessage(id = message.id, payload = listOf(error)))
            }
            .onCompletion { emit(GraphQLWebSocketMessage.CompleteMessage(id = message.id)) }
    }

    private fun onPing(): Flow<GraphQLWebSocketMessage.PongMessage> {
        logger.debug("received ping")
        val response = GraphQLWebSocketMessage.PongMessage(payload = emptyMap())
        return flowOf(response)
    }
}
