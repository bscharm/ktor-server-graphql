package com.bscharm.ktor.server.plugins.graphql.subscriptions

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import graphql.ExecutionResultImpl
import graphql.GraphqlErrorBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

internal class GraphQLWebSocketMessageDeserializationTest {
    private val mapper = jacksonObjectMapper()

    companion object {
        @JvmStatic
        fun `default mapper can deserialize messages with an optional payload`(): Stream<Arguments> {
            return Stream.of(
                Arguments { arrayOf("connection_init", GraphQLWebSocketMessage.ConnectionInitMessage::class.java) },
                Arguments { arrayOf("connection_ack", GraphQLWebSocketMessage.ConnectionAckMessage::class.java) },
                Arguments { arrayOf("ping", GraphQLWebSocketMessage.PingMessage::class.java) },
                Arguments { arrayOf("pong", GraphQLWebSocketMessage.PongMessage::class.java) },
            )
        }
    }

    @ParameterizedTest(name = "{0} is supported")
    @MethodSource
    fun `default mapper can deserialize messages with an optional payload`(type: String, clazz: Class<GraphQLWebSocketMessage>) {
        val message: GraphQLWebSocketMessage = mapper.readValue("{\"type\":\"$type\", \"payload\": {\"foo\": \"bar\"}}")
        assertThat(message).isInstanceOf(clazz)
    }

    @Test
    fun `default mapper can deserialize a subscription message`() {
        val message: GraphQLWebSocketMessage =
            mapper.readValue("{\"type\":\"subscribe\", \"id\": \"1\",\"payload\": {\"query\": \"bar\"}}")
        assertThat(message).isInstanceOf(GraphQLWebSocketMessage.SubscriptionMessage::class.java)
    }

    @Test
    fun `default mapper can deserialize a complete message`() {
        val message: GraphQLWebSocketMessage =
            mapper.readValue("{\"type\":\"complete\", \"id\": \"1\"}")
        assertThat(message).isInstanceOf(GraphQLWebSocketMessage.CompleteMessage::class.java)
    }

    @Test
    fun `default mapper can serialize a next message`() {
        val message = GraphQLWebSocketMessage.NextMessage(
            id = "id",
            payload = ExecutionResultImpl(emptyList())
        )

        val json = mapper.writeValueAsString(message)

        assertThat(json).isEqualTo("{\"id\":\"id\",\"payload\":{\"errors\":[],\"data\":null,\"extensions\":null,\"dataPresent\":false},\"type\":\"next\"}")
    }

    @Test
    fun `default mapper can serialize an error message`() {
        val message = GraphQLWebSocketMessage.ErrorMessage(
            id = "id",
            payload = listOf(GraphqlErrorBuilder.newError().message("something went wrong").build())
        )

        val json = mapper.writeValueAsString(message)

        assertThat(json).isEqualTo("{\"id\":\"id\",\"payload\":[{\"message\":\"something went wrong\",\"locations\":[],\"errorType\":\"DataFetchingException\",\"path\":null,\"extensions\":null}],\"type\":\"error\"}")
    }

    @Test
    fun `default mapper returns an invalid message if type not recognized`() {
        val message = mapper.readValue("{\"type\": \"nope\"}", GraphQLWebSocketMessage::class.java)
        assertThat(message).isInstanceOf(GraphQLWebSocketMessage.InvalidMessage::class.java)
    }
}
