package com.cluster

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent

class Reactor : RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    override fun handleRequest(apiGatewayProxyRequestEvent: APIGatewayProxyRequestEvent, context: Context): APIGatewayProxyResponseEvent {
        println(apiGatewayProxyRequestEvent.path)
        println(apiGatewayProxyRequestEvent.body)
        return APIGatewayProxyResponseEvent().withStatusCode(200).withBody("Hello World!")
    }
}