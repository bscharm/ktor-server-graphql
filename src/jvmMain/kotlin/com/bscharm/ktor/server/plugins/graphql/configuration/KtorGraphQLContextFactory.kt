package com.bscharm.ktor.server.plugins.graphql.configuration

import com.expediagroup.graphql.generator.execution.GraphQLContext
import com.expediagroup.graphql.server.execution.GraphQLContextFactory
import io.ktor.server.request.ApplicationRequest

class KtorGraphQLContextFactory(private val contextMap: Map<Any, Any>) :
    GraphQLContextFactory<GraphQLContext, ApplicationRequest> {
    override suspend fun generateContextMap(request: ApplicationRequest): Map<Any, Any> = contextMap
}
