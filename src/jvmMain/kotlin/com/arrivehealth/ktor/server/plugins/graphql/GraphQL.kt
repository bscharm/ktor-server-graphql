package com.arrivehealth.ktor.server.plugins.graphql

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
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

@Suppress("Unused")
val GraphQL = createApplicationPlugin(
    name = "GraphQL", createConfiguration = ::KtorGraphQLPluginConfiguration
) {
    val queries = pluginConfig.queries.map { TopLevelObject(it) }
    val mutations = pluginConfig.mutations.map { TopLevelObject(it) }
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
    val schema: GraphQLSchema = toSchema(config, queries, mutations)
    val graphQL: GraphQL = graphql.GraphQL.newGraphQL(schema).valueUnboxer(IDValueUnboxer()).build()
    val graphQLServer: GraphQLServer<ApplicationRequest> = KtorGraphQLServer(
        KtorGraphQLRequestParser(mapper), KtorGraphQLContextFactory(emptyMap()), GraphQLRequestHandler(graphQL)
    )

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
    var path: String = "graphql"
    var hooks: SchemaGeneratorHooks = NoopSchemaGeneratorHooks
    var playground: Boolean = false
    var playgroundPath: String = "playground"
}

private fun buildPlaygroundHtml(graphQLPath: String, subscriptionsPath: String) =
    Application::class.java.classLoader.getResource("graphql-playground.html")?.readText()
        ?.replace("\${graphQLEndpoint}", graphQLPath)
        ?.replace("\${subscriptionsEndpoint}", subscriptionsPath)
        ?: throw IllegalStateException("graphql-playground.html cannot be found in the classpath")
