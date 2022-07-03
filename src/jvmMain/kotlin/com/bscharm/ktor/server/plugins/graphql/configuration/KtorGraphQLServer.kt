package com.bscharm.ktor.server.plugins.graphql.configuration

import com.expediagroup.graphql.server.execution.GraphQLContextFactory
import com.expediagroup.graphql.server.execution.GraphQLRequestHandler
import com.expediagroup.graphql.server.execution.GraphQLRequestParser
import com.expediagroup.graphql.server.execution.GraphQLServer
import io.ktor.server.request.ApplicationRequest

class KtorGraphQLServer(
    requestParser: GraphQLRequestParser<ApplicationRequest>,
    contextFactory: GraphQLContextFactory<*, ApplicationRequest>,
    requestHandler: GraphQLRequestHandler
) : GraphQLServer<ApplicationRequest>(requestParser, contextFactory, requestHandler)
