package com.cluster.api

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import com.amazonaws.util.Base64
import com.auth0.jwt.JWT
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

class ApiReactor(
        private val handlers: List<ApiHandler> = listOf(Accounts())
): RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    override fun handleRequest(apiGatewayProxyRequestEvent: APIGatewayProxyRequestEvent, context: Context?): APIGatewayProxyResponseEvent {
        val token = JWT.decode(apiGatewayProxyRequestEvent.headers["Authorization"]!!.substring("Bearer ".length))
        val payload = String(Base64.decode(token.payload))
        val username = jacksonObjectMapper().readValue<JsonNode>(payload)["cognito:username"].textValue()
        val handler = handlers.find { it.resources.contains(apiGatewayProxyRequestEvent.resource) }
        val response = when(handler){
            null -> APIGatewayProxyResponseEvent().withStatusCode(404)
            else -> handler.handle(apiGatewayProxyRequestEvent.resource,username, apiGatewayProxyRequestEvent)
        }
        return response.withHeaders(response.headers.orEmpty() + mapOf(
                "Access-Control-Allow-Origin" to "*"
        ))
    }
    interface ApiHandler{
        val resources: List<String>
        fun handle(resource: String, username: String, event: APIGatewayProxyRequestEvent): APIGatewayProxyResponseEvent
    }
}