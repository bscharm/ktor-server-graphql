package com.arrivehealth.ktor.server.plugins.graphql

import com.arrivehealth.ktor.server.plugins.graphql.subscriptions.KtorGraphQLSubscriptionHandler
import com.arrivehealth.ktor.server.plugins.graphql.subscriptions.KtorGraphQLWebSocketProtocolHandler
import com.expediagroup.graphql.generator.SchemaGeneratorConfig
import com.expediagroup.graphql.generator.TopLevelObject
import com.expediagroup.graphql.generator.execution.FlowSubscriptionExecutionStrategy
import com.expediagroup.graphql.generator.hooks.NoopSchemaGeneratorHooks
import com.expediagroup.graphql.generator.hooks.SchemaGeneratorHooks
import com.expediagroup.graphql.generator.scalars.IDValueUnboxer
import com.expediagroup.graphql.generator.toSchema
import com.expediagroup.graphql.server.execution.GraphQLRequestHandler
import com.expediagroup.graphql.server.execution.GraphQLServer
import com.expediagroup.graphql.server.operations.Mutation
import com.expediagroup.graphql.server.operations.Query
import com.expediagroup.graphql.server.operations.Subscription
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import io.ktor.server.application.pluginOrNull
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

@Suppress("Unused")
val GraphQL = createApplicationPlugin(
    name = "GraphQL", createConfiguration = ::KtorGraphQLPluginConfiguration
) {
    val logger: Logger = LoggerFactory.getLogger("KtorGraphQLPlugin")

    val queries = pluginConfig.queries.map { TopLevelObject(it) }
    val mutations = pluginConfig.mutations.map { TopLevelObject(it) }
    val subscriptions = pluginConfig.subscriptions.map { TopLevelObject(it) }
    val subscriptionsPath = pluginConfig.subscriptionsPath
    val packages = pluginConfig.packages
    val path = pluginConfig.path
    val hooks = pluginConfig.hooks
    val playground = pluginConfig.playground
    val playgroundPath = pluginConfig.playgroundPath
    val authenticationEnabled = pluginConfig.authentication
    val authenticationName = pluginConfig.authenticationName
    val contextFactory = pluginConfig.contextFactory

    val mapper = jacksonObjectMapper()
    val config = SchemaGeneratorConfig(
        supportedPackages = packages,
        hooks = hooks,
    )
    val schema: GraphQLSchema = toSchema(config, queries, mutations, subscriptions)
    val graphQL: GraphQL = graphql.GraphQL.newGraphQL(schema).valueUnboxer(IDValueUnboxer())
        .subscriptionExecutionStrategy(FlowSubscriptionExecutionStrategy()).build()
    val graphQLServer: GraphQLServer<ApplicationRequest> = KtorGraphQLServer(
        KtorGraphQLRequestParser(mapper), KtorGraphQLContextFactory(contextFactory), GraphQLRequestHandler(graphQL)
    )
    val subscriptionHandler = KtorGraphQLSubscriptionHandler(graphQL)
    val protocolHandler = KtorGraphQLWebSocketProtocolHandler(subscriptionHandler)

    this.application.install(WebSockets)
    this.application.routing {
        val authenticationPlugin = this.application.pluginOrNull(Authentication)

        if (authenticationPlugin != null && authenticationEnabled) {
            authenticate(authenticationName) {
                graphQLRoute(path, graphQLServer)
                webSocketRoute(subscriptionsPath, logger, protocolHandler, mapper)
            }
        } else {
            graphQLRoute(path, graphQLServer)
            webSocketRoute(subscriptionsPath, logger, protocolHandler, mapper)
        }

        if (playground) {
            get(playgroundPath) {
                call.respondText(buildPlaygroundHtml(path, subscriptionsPath), ContentType.Text.Html)
            }
        }
    }
}

private fun Route.webSocketRoute(
    subscriptionsPath: String,
    logger: Logger,
    protocolHandler: KtorGraphQLWebSocketProtocolHandler,
    mapper: ObjectMapper
) {
    webSocket(subscriptionsPath, protocol = "graphql-transport-ws") {
        val session = this
        val sessionId = UUID.randomUUID()
        incoming.consumeEach { frame ->
            launch(Dispatchers.IO) {
                logger.trace(String(frame.data))
                protocolHandler.handle(frame, session, sessionId, coroutineContext.job)
                    .map { mapper.writeValueAsString(it) }
                    .map { Frame.Text(it) }
                    .onEach { frame ->
                        logger.trace(String(frame.data))
                        send(frame)
                    }
                    .cancellable()
                    .collect()
            }
        }
    }
}

private fun Route.graphQLRoute(
    path: String,
    graphQLServer: GraphQLServer<ApplicationRequest>
) {
    route(path, HttpMethod.Post) {
        val contentNegotiationPlugin = this.application.pluginOrNull(ContentNegotiation)
        if (contentNegotiationPlugin == null) {
            install(ContentNegotiation) {
                jackson()
            }
        }

        handle {
            val result = graphQLServer.execute(call.request)

            result?.also { call.respond(it) } ?: run {
                call.respond(HttpStatusCode.BadRequest)
            }
        }
    }
}

class KtorGraphQLPluginConfiguration {
    var queries: List<Query> = emptyList()
    var mutations: List<Mutation> = emptyList()
    var subscriptions: List<Subscription> = emptyList()
    var packages: List<String> = emptyList()
    var hooks: SchemaGeneratorHooks = NoopSchemaGeneratorHooks
    var contextFactory: ContextFactoryFunction = NoopContextFactoryFunction
    var playground: Boolean = false
    var authentication: Boolean = false
    var authenticationName: String? = null
    var path: String = "graphql"
    var subscriptionsPath: String = "subscriptions"
    var playgroundPath: String = "playground"
}

private fun buildPlaygroundHtml(graphQLPath: String, subscriptionsPath: String) =
    Application::class.java.classLoader.getResource("graphql-playground.html")?.readText()
        ?.replace("\${graphQLEndpoint}", graphQLPath)?.replace("\${subscriptionsEndpoint}", subscriptionsPath)
        ?: throw IllegalStateException("graphql-playground.html cannot be found in the classpath")
