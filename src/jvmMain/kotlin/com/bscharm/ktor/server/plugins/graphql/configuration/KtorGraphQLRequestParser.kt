package com.bscharm.ktor.server.plugins.graphql.configuration

import com.expediagroup.graphql.server.execution.GraphQLRequestParser
import com.expediagroup.graphql.server.types.GraphQLServerRequest
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.receiveText

class KtorGraphQLRequestParser(private val mapper: ObjectMapper) : GraphQLRequestParser<ApplicationRequest> {
    override suspend fun parseRequest(request: ApplicationRequest): GraphQLServerRequest? =
        request.call.receiveText().let(::parseRequest).getOrNull()

    private fun parseRequest(rawRequest: String): Result<GraphQLServerRequest> = kotlin.runCatching {
        mapper.readValue(rawRequest, GraphQLServerRequest::class.java)
    }
}
