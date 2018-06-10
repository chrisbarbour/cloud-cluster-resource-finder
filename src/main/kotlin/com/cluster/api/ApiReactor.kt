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
        private val apis: List<ApiGatewayHandler> = listOf(UserHandler(), AccountHandler())
): RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    override fun handleRequest(apiGatewayProxyRequestEvent: APIGatewayProxyRequestEvent, context: Context?): APIGatewayProxyResponseEvent {
        val token = JWT.decode(apiGatewayProxyRequestEvent.headers["Authorization"]!!.substring("Bearer ".length))
        val payload = String(Base64.decode(token.payload))
        val username = jacksonObjectMapper().readValue<JsonNode>(payload)["cognito:username"].textValue()
        val resourceHandler = apis.find { it.resources().containsKey(apiGatewayProxyRequestEvent.resource) }?.resources().orEmpty()[apiGatewayProxyRequestEvent.resource]
        val handler = resourceHandler.orEmpty()[apiGatewayProxyRequestEvent.httpMethod.toUpperCase()]
        val response = when(handler){
            null -> APIGatewayProxyResponseEvent().withStatusCode(404)
            else -> handler(AuthorizedEvent(username, apiGatewayProxyRequestEvent))
        }
        return response.withHeaders(response.headers.orEmpty() + mapOf(
                "Access-Control-Allow-Origin" to "*"
        ))
    }
    interface ApiGatewayHandler: Api<AuthorizedEvent, APIGatewayProxyResponseEvent>
    open class AuthorizedEvent(val username: String, event: APIGatewayProxyRequestEvent): Event<APIGatewayProxyRequestEvent>(event)
    interface Api<in T: Event<*>, out R>{ fun resources(): Map<String, Map<String, (event: T) -> R>> }
    open class Event<out T>(val payload: T)

}