package com.bscharm.ktor.server.plugins.graphql

val NoopContextFactoryFunction: ContextFactoryFunction = { _ -> emptyMap() }
