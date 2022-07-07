package com.arrivehealth.ktor.server.plugins.graphql

import com.arrivehealth.ktor.server.plugins.graphql.testQueries.ComplexQuery
import com.arrivehealth.ktor.server.plugins.graphql.testQueries.ComplexQueryResult
import com.arrivehealth.ktor.server.plugins.graphql.testQueries.SimpleQuery
import com.arrivehealth.ktor.server.plugins.graphql.testQueries.SimpleQueryResult
import com.expediagroup.graphql.generator.exceptions.TypeNotSupportedException
import com.expediagroup.graphql.server.types.GraphQLRequest
import com.expediagroup.graphql.server.types.GraphQLResponse
import com.expediagroup.graphql.server.types.GraphQLServerRequest
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.testApplication
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class KtorGraphQLPluginTest {
    @Test
    fun `supports configurable queries`() = testApplication {
        install(GraphQL) {
            queries = listOf(SimpleQuery())
        }

        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                jackson()
            }
        }

        val response: HttpResponse = client.post("/graphql") {
            contentType(ContentType.Application.Json)
            val request: GraphQLServerRequest = GraphQLRequest(
                query = "{ value }"
            )
            setBody(request)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)

        val body = response.body<GraphQLResponse<SimpleQueryResult>>()
        assertThat(body.data?.value).isEqualTo("success")
    }

    @Test
    fun `does not expose playground by default`() = testApplication {
        install(GraphQL) {
            queries = listOf(SimpleQuery())
        }

        val client = createClient {}

        val response: HttpResponse = client.get("/playground")

        assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun `exposes playground using default path if enabled`() = testApplication {
        install(GraphQL) {
            queries = listOf(SimpleQuery())
            playground = true
        }

        val client = createClient {}

        val response: HttpResponse = client.get("/playground")

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.bodyAsText()).startsWith("<!DOCTYPE html>")
    }

    @Test
    fun `exposes playground at configured path if enabled`() = testApplication {
        install(GraphQL) {
            queries = listOf(SimpleQuery())
            playground = true
            playgroundPath = "myAlternatePlayground"
        }

        val client = createClient {}

        assertThat(client.get("/playground").status).isEqualTo(HttpStatusCode.NotFound)

        val response: HttpResponse = client.get("/myAlternatePlayground")

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.bodyAsText()).startsWith("<!DOCTYPE html>")
    }

    @Test
    fun `supports complex types if registered in packages`() = testApplication {
        install(GraphQL) {
            queries = listOf(ComplexQuery())
            packages = listOf("com.arrivehealth.ktor.server.plugins.graphql.testSchema")
        }

        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                jackson()
            }
        }

        val response: HttpResponse = client.post("/graphql") {
            contentType(ContentType.Application.Json)
            val request: GraphQLServerRequest = GraphQLRequest(
                query = "{ complexValue { value } }"
            )
            setBody(request)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)

        val body = response.body<GraphQLResponse<ComplexQueryResult>>()
        assertThat(body.data?.complexValue?.value).isEqualTo("success")
    }

    @Test
    fun `supports an application with the content negotiation plugin already installed`() {
        assertDoesNotThrow {
            testApplication {
                install(ContentNegotiation) {
                    jackson()
                }

                install(GraphQL) {
                    queries = listOf(ComplexQuery())
                    packages = listOf("com.arrivehealth.ktor.server.plugins.graphql.testSchema")
                }
            }
        }
    }

    @Test
    fun `fails to initialize on complex types which are not registered in packages`() {
        assertThatThrownBy {
            testApplication {
                install(GraphQL) {
                    queries = listOf(ComplexQuery())
                }
            }
        }.isInstanceOf(TypeNotSupportedException::class.java)
    }
}
