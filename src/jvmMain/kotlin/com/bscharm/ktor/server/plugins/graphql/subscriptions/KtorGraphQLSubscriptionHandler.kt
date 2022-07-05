package com.bscharm.ktor.server.plugins.graphql.subscriptions

import com.expediagroup.graphql.server.extensions.toExecutionInput
import com.expediagroup.graphql.server.types.GraphQLRequest
import graphql.ExecutionResult
import graphql.GraphQL
import org.reactivestreams.Publisher

class KtorGraphQLSubscriptionHandler(private val graphQL: GraphQL) {
    fun execute(graphQLRequest: GraphQLRequest): Publisher<ExecutionResult> {
        val input = graphQLRequest.toExecutionInput()
        return graphQL.execute(input).getData()
    }
}
