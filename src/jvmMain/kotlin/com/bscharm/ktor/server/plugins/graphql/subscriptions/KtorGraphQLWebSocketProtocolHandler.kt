package com.bscharm.ktor.server.plugins.graphql.subscriptions

import com.expediagroup.graphql.server.extensions.toGraphQLError
import com.expediagroup.graphql.server.types.GraphQLRequest
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import org.slf4j.LoggerFactory
import java.util.UUID

class KtorGraphQLWebSocketProtocolHandler(private val subscriptionHandler: KtorGraphQLSubscriptionHandler) {
    private val logger = LoggerFactory.getLogger("KtorGraphQLPlugin")
    private val mapper = jacksonObjectMapper()
    private val state = KtorGraphQLWebSocketSessionState()

    fun initReceived(sessionId: UUID): Boolean {
        return state.isInitialized(sessionId)
    }

    suspend fun handle(
        frame: Frame,
        session: WebSocketServerSession,
        sessionId: UUID,
        job: Job
    ): Flow<GraphQLWebSocketMessage> {
        return when (val message = graphQLWebSocketMessage(frame)) {
            is GraphQLWebSocketMessage.PingMessage -> onPing()
            is GraphQLWebSocketMessage.PongMessage -> emptyFlow()
            is GraphQLWebSocketMessage.ConnectionInitMessage -> onConnectionInit(session, sessionId)
            is GraphQLWebSocketMessage.SubscriptionMessage -> onSubscribe(message, session, sessionId, job)
            is GraphQLWebSocketMessage.CompleteMessage -> onComplete(message, sessionId)
            else -> {
                logger.debug("closing session[{}]: received unrecognized message", sessionId)
                session.close(CloseReason(GraphQLWsCloseReason.UnrecognizedMessage.Code, "Unrecognized message"))
                emptyFlow()
            }
        }
    }

    private fun graphQLWebSocketMessage(frame: Frame): GraphQLWebSocketMessage? = kotlin.runCatching {
        return mapper.readValue<GraphQLWebSocketMessage>(frame.data)
    }.getOrNull()

    private fun onComplete(
        message: GraphQLWebSocketMessage.CompleteMessage,
        sessionId: UUID
    ): Flow<GraphQLWebSocketMessage> {
        logger.debug("received complete")
        state.cancelOperation(message.id, sessionId)
        logger.debug("operation ${message.id} cancelled")
        return emptyFlow()
    }

    private suspend fun onConnectionInit(
        session: WebSocketServerSession,
        sessionId: UUID
    ): Flow<GraphQLWebSocketMessage.ConnectionAckMessage> {
        logger.debug("received init")

        if (state.isInitialized(sessionId)) {
            logger.debug("closing session[{}]: received duplicate init", sessionId)
            session.close(
                CloseReason(
                    GraphQLWsCloseReason.DuplicateInitialization.Code,
                    "Too many initialization requests."
                )
            )
            return emptyFlow()
        } else {
            state.initialize(sessionId)
            logger.debug("session initialized")
        }

        val response = GraphQLWebSocketMessage.ConnectionAckMessage(payload = emptyMap())
        return flowOf(response)
    }

    private suspend fun onSubscribe(
        message: GraphQLWebSocketMessage.SubscriptionMessage,
        session: WebSocketServerSession,
        sessionId: UUID,
        job: Job
    ): Flow<GraphQLWebSocketMessage> {
        logger.debug("received subscribe")

        if (!state.isInitialized(sessionId)) {
            logger.debug("closing session[{}]: received subscribe before init", sessionId)
            session.close(CloseReason(GraphQLWsCloseReason.Unauthorized.Code, "Unauthorized."))
            return emptyFlow()
        }

        if (state.operationSubscribed(message.id, sessionId)) {
            logger.debug("closing session[{}]: received duplicate subscribe for operation[{}]", sessionId, message.id)
            session.close(
                CloseReason(
                    GraphQLWsCloseReason.SubscriberExists.Code,
                    "Subscriber for ${message.id} already exists."
                )
            )
        }

        state.markOperationSubscribed(message.id, sessionId, job)
        logger.debug("session[{}] subscribed to operation[{}]", sessionId, message.id)
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
            .onCompletion {
                emit(GraphQLWebSocketMessage.CompleteMessage(id = message.id))
                state.cancelOperation(message.id, sessionId)
            }
    }

    private fun onPing(): Flow<GraphQLWebSocketMessage.PongMessage> {
        logger.debug("received ping")
        val response = GraphQLWebSocketMessage.PongMessage(payload = emptyMap())
        return flowOf(response)
    }
}
