package com.bscharm.ktor.server.plugins.graphql

import com.bscharm.ktor.server.plugins.graphql.subscriptions.GraphQLWebSocketMessage
import com.bscharm.ktor.server.plugins.graphql.testQueries.SimpleQuery
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.headers
import io.ktor.serialization.jackson.jackson
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class KtorGraphQLSubscriptionsTest {
    private val mapper = jacksonObjectMapper()

    @Test
    fun `responds to ping with pong`() = testApplication {
        install(GraphQL) {
            queries = listOf(SimpleQuery())
        }

        val client = createClient {
            install(ContentNegotiation) {
                jackson()
            }

            install(WebSockets)
        }

        client.webSocket(path = "subscriptions", request = {
            headers {
                append("Sec-WebSocket-Protocol", "graphql-transport-ws")
            }
        }) {
            val pingMessage = GraphQLWebSocketMessage.PingMessage(null)
            send(Frame.Text(mapper.writeValueAsString(pingMessage)))

            val frame: Frame.Text = incoming.receive() as Frame.Text
            assertThat(frame.readText()).isEqualTo("{\"payload\":{},\"type\":\"pong\"}")
        }

        client.close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `accepts unidirectional pong message`() = testApplication {
        install(GraphQL) {
            queries = listOf(SimpleQuery())
        }

        val client = createClient {
            install(ContentNegotiation) {
                jackson()
            }

            install(WebSockets)
        }

        client.webSocket(path = "subscriptions", request = {
            headers {
                append("Sec-WebSocket-Protocol", "graphql-transport-ws")
            }
        }) {
            val pingMessage = GraphQLWebSocketMessage.PingMessage(null)
            val pongMessage = GraphQLWebSocketMessage.PongMessage(null)

            send(Frame.Text(mapper.writeValueAsString(pongMessage)))
            assertThat(incoming.isEmpty).isTrue()
            send(Frame.Text(mapper.writeValueAsString(pingMessage)))

            val frame: Frame.Text = incoming.receive() as Frame.Text
            assertThat(frame.readText()).isEqualTo("{\"payload\":{},\"type\":\"pong\"}")
        }

        client.close()
    }

    @Test
    fun `responds to connection init with connection ack`() = testApplication {
        install(GraphQL) {
            queries = listOf(SimpleQuery())
        }

        val client = createClient {
            install(ContentNegotiation) {
                jackson()
            }

            install(WebSockets)
        }

        client.webSocket(path = "subscriptions", request = {
            headers {
                append("Sec-WebSocket-Protocol", "graphql-transport-ws")
            }
        }) {
            val initMessage = GraphQLWebSocketMessage.ConnectionInitMessage(null)
            send(Frame.Text(mapper.writeValueAsString(initMessage)))

            val frame: Frame.Text = incoming.receive() as Frame.Text
            assertThat(frame.readText()).isEqualTo("{\"payload\":{},\"type\":\"connection_ack\"}")
        }

        client.close()
    }

    @Test
    fun `closes the connection for duplicate init messages`() = testApplication {
        install(GraphQL) {
            queries = listOf(SimpleQuery())
        }

        val client = createClient {
            install(ContentNegotiation) {
                jackson()
            }

            install(WebSockets)
        }

        client.webSocket(path = "subscriptions", request = {
            headers {
                append("Sec-WebSocket-Protocol", "graphql-transport-ws")
            }
        }) {
            val initMessage = GraphQLWebSocketMessage.ConnectionInitMessage(null)
            send(Frame.Text(mapper.writeValueAsString(initMessage)))

            val frame: Frame.Text = incoming.receive() as Frame.Text
            assertThat(frame.readText()).isEqualTo("{\"payload\":{},\"type\":\"connection_ack\"}")

            send(Frame.Text(mapper.writeValueAsString(initMessage)))

            assertThat(incoming.receiveCatching().isClosed).isTrue()
        }

        client.close()
    }
}
