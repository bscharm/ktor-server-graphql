package com.bscharm.ktor.server.plugins.graphql.subscriptions

enum class GraphQLWsCloseReason(val Code: Short) {
    UnrecognizedMessage(4400),
    Unauthorized(4401),
    SubscriberExists(4409),
    DuplicateInitialization(4429)
}

