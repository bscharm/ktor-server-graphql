package com.arrivehealth.ktor.server.plugins.graphql.testQueries

import com.expediagroup.graphql.server.operations.Query

class SimpleQuery : Query {
    fun value(): String {
        return "success"
    }
}

data class SimpleQueryResult(val value: String)
