package com.cluster.api

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import com.cluster.data.DataFinder
import com.cluster.data.S3DataFinder
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

class UserHandler(val dataFinder: DataFinder = S3DataFinder()): ApiReactor.ApiGatewayHandler {

    override fun resources() = mapOf(
            "/users/{$USERNAME}" to mapOf("GET" to ::user)
    )

    fun user(event: ApiReactor.AuthorizedEvent): APIGatewayProxyResponseEvent {
        val requestedUsername = event.payload.pathParameters[USERNAME]!!
        return if(requestedUsername == event.username) {
            val user = dataFinder.userInfoFor(requestedUsername)
            val userAsString = jackson.writeValueAsString(user)
            APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(userAsString)
        }
        else APIGatewayProxyResponseEvent()
                .withStatusCode(403)
                .withBody("The requested username must match the requesting username")
    }

    companion object {
        private val USERNAME = "username"
        private val jackson = jacksonObjectMapper()
    }
}