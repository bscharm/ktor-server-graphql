package com.bscharm.ktor.server.plugins.graphql.subscriptions

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import graphql.ExecutionResult
import graphql.ExecutionResultImpl
import graphql.GraphQLError

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true,
    defaultImpl = GraphQLWebSocketMessage.InvalidMessage::class
)
@JsonIgnoreProperties(ignoreUnknown = true)
sealed class GraphQLWebSocketMessage(open val type: String) {
    sealed interface Incoming
    sealed interface Outgoing

    @JsonTypeName("connection_init")
    data class ConnectionInitMessage(val payload: Map<String, Any>?) :
        GraphQLWebSocketMessage("connection_init"),
        Incoming

    @JsonTypeName("connection_ack")
    data class ConnectionAckMessage(val payload: Map<String, Any>?) :
        GraphQLWebSocketMessage("connection_ack"),
        Incoming

    @JsonTypeName("ping")
    data class PingMessage(val payload: Map<String, Any>?) : GraphQLWebSocketMessage("ping"), Incoming, Outgoing

    @JsonTypeName("pong")
    data class PongMessage(val payload: Map<String, Any>?) : GraphQLWebSocketMessage("pong"), Incoming, Outgoing

    @JsonTypeName("subscribe")
    data class SubscriptionMessage(val id: String, val payload: SubscriptionMessagePayload) :
        GraphQLWebSocketMessage("subscribe"), Incoming {
        data class SubscriptionMessagePayload(
            val operationName: String?,
            val query: String,
            val variables: Map<String, Any>?,
            val extensions: Map<String, Any>?,
        )
    }

    @JsonTypeName("next")
    data class NextMessage(val id: String, val payload: ExecutionResult) : GraphQLWebSocketMessage("next"), Outgoing

    @JsonTypeName("error")
    data class ErrorMessage(val id: String, val payload: List<GraphQLError>) :
        GraphQLWebSocketMessage("error"),
        Outgoing

    @JsonTypeName("complete")
    data class CompleteMessage(val id: String) : GraphQLWebSocketMessage("complete"), Incoming, Outgoing

    object InvalidMessage : GraphQLWebSocketMessage("invalid"), Incoming, Outgoing
}
