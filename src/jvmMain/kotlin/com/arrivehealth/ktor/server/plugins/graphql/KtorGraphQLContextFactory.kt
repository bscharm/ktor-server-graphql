package com.arrivehealth.ktor.server.plugins.graphql

import com.expediagroup.graphql.generator.execution.GraphQLContext
import com.expediagroup.graphql.server.execution.GraphQLContextFactory
import io.ktor.server.request.ApplicationRequest

internal class KtorGraphQLContextFactory(private val contextMap: ContextFactoryFunction) :
    GraphQLContextFactory<GraphQLContext, ApplicationRequest> {
    override suspend fun generateContextMap(request: ApplicationRequest): Map<Any, Any> = contextMap.invoke(request)
}

typealias ContextFactoryFunction = suspend (request: ApplicationRequest) -> Map<Any, Any>
