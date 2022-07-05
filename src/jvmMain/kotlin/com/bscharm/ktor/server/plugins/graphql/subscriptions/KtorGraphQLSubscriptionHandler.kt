package com.bscharm.ktor.server.plugins.graphql.subscriptions

import com.expediagroup.graphql.server.extensions.toExecutionInput
import com.expediagroup.graphql.server.types.GraphQLRequest
import graphql.ExecutionResult
import graphql.GraphQL
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class KtorGraphQLSubscriptionHandler(private val graphQL: GraphQL) {
    @Suppress("UNCHECKED_CAST", "KotlinConstantConditions")
    fun execute(graphQLRequest: GraphQLRequest): Flow<ExecutionResult> {
        val input = graphQLRequest.toExecutionInput()

        val executionResult = graphQL.execute(input)
        return kotlin.runCatching {
            executionResult.getData<Flow<ExecutionResult>>()
        }.getOrElse { flowOf(executionResult) }
    }
}
