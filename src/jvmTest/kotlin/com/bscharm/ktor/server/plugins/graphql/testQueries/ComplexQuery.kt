package com.bscharm.ktor.server.plugins.graphql.testQueries

import com.bscharm.ktor.server.plugins.graphql.testSchema.ComplexType
import com.expediagroup.graphql.server.operations.Query

class ComplexQuery : Query {
    fun complexValue(): ComplexType {
        return ComplexType(value = "success")
    }
}

data class ComplexQueryResult(val complexValue: ComplexType)
