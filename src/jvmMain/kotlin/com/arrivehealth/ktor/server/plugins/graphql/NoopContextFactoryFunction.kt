package com.arrivehealth.ktor.server.plugins.graphql

val NoopContextFactoryFunction: ContextFactoryFunction = { _ -> emptyMap() }
