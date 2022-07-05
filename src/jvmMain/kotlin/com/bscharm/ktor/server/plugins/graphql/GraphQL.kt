package com.bscharm.ktor.server.plugins.graphql

import com.bscharm.ktor.server.plugins.graphql.subscriptions.GraphQLWebSocketMessage
import com.bscharm.ktor.server.plugins.graphql.subscriptions.KtorGraphQLSubscriptionHandler
import com.bscharm.ktor.server.plugins.graphql.subscriptions.KtorGraphQLWebSocketProtocolHandler
import com.expediagroup.graphql.generator.SchemaGeneratorConfig
import com.expediagroup.graphql.generator.TopLevelObject
import com.expediagroup.graphql.generator.hooks.NoopSchemaGeneratorHooks
import com.expediagroup.graphql.generator.hooks.SchemaGeneratorHooks
import com.expediagroup.graphql.generator.scalars.IDValueUnboxer
import com.expediagroup.graphql.generator.toSchema
import com.expediagroup.graphql.server.execution.GraphQLRequestHandler
import com.expediagroup.graphql.server.execution.GraphQLServer
import com.expediagroup.graphql.server.operations.Mutation
import com.expediagroup.graphql.server.operations.Query
import com.expediagroup.graphql.server.operations.Subscription
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import graphql.GraphQL
import graphql.schema.GraphQLSchema
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@OptIn(FlowPreview::class)
@Suppress("Unused")
val GraphQL = createApplicationPlugin(
    name = "GraphQL", createConfiguration = ::KtorGraphQLPluginConfiguration
) {
    val logger: Logger = LoggerFactory.getLogger("KtorGraphQLPlugin")

    val queries = pluginConfig.queries.map { TopLevelObject(it) }
    val mutations = pluginConfig.mutations.map { TopLevelObject(it) }
    val subscriptions = pluginConfig.subscriptions.map { TopLevelObject(it) }
    val packages = pluginConfig.packages
    val path = pluginConfig.path
    val hooks = pluginConfig.hooks
    val playground = pluginConfig.playground
    val playgroundPath = pluginConfig.playgroundPath

    val mapper = jacksonObjectMapper()
    val config = SchemaGeneratorConfig(
        supportedPackages = packages,
        hooks = hooks,
    )
    val schema: GraphQLSchema = toSchema(config, queries, mutations, subscriptions)
    val graphQL: GraphQL = graphql.GraphQL.newGraphQL(schema).valueUnboxer(IDValueUnboxer()).build()
    val graphQLServer: GraphQLServer<ApplicationRequest> = KtorGraphQLServer(
        KtorGraphQLRequestParser(mapper), KtorGraphQLContextFactory(emptyMap()), GraphQLRequestHandler(graphQL)
    )
    val subscriptionHandler = KtorGraphQLSubscriptionHandler(graphQL)
    val protocolHandler = KtorGraphQLWebSocketProtocolHandler(subscriptionHandler)

    this.application.install(WebSockets)
    this.application.routing {
        route(path, HttpMethod.Post) {
            install(ContentNegotiation) {
                jackson()
            }

            handle {
                val result = graphQLServer.execute(call.request)

                result?.also { call.respond(it) } ?: run {
                    call.respond(HttpStatusCode.BadRequest)
                }
            }
        }

        webSocket("subscriptions", protocol = "graphql-transport-ws") {
            val session = this

            launch(Dispatchers.IO) {
                delay(10000)
                if (!protocolHandler.initReceived(session)) {
                    close(CloseReason(4408, "Connection initialization timeout."))
                }
            }

            incoming
                .receiveAsFlow()
                .onEach { frame -> logger.trace(String(frame.data)) }
                .map { mapper.readValue<GraphQLWebSocketMessage>(it.data) }
                .flatMapConcat { webSocketMessage -> protocolHandler.handle(webSocketMessage, session) }
                .map { mapper.writeValueAsString(it) }
                .map { send(Frame.Text(it)) }
                .collect()
        }

        if (playground) {
            get(playgroundPath) {
                call.respondText(buildPlaygroundHtml(path, "subscriptions"), ContentType.Text.Html)
            }
        }
    }
}

class KtorGraphQLPluginConfiguration {
    var packages: List<String> = emptyList()
    var queries: List<Query> = emptyList()
    var mutations: List<Mutation> = emptyList()
    var subscriptions: List<Subscription> = emptyList()
    var path: String = "graphql"
    var hooks: SchemaGeneratorHooks = NoopSchemaGeneratorHooks
    var playground: Boolean = false
    var playgroundPath: String = "playground"
}

private fun buildPlaygroundHtml(graphQLPath: String, subscriptionsPath: String) =
    Application::class.java.classLoader.getResource("graphql-playground.html")?.readText()
        ?.replace("\${graphQLEndpoint}", graphQLPath)?.replace("\${subscriptionsEndpoint}", subscriptionsPath)
        ?: throw IllegalStateException("graphql-playground.html cannot be found in the classpath")
