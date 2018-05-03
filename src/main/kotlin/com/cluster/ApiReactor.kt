package com.cluster

import com.amazonaws.services.iot.AWSIot
import com.amazonaws.services.iot.AWSIotClient
import com.amazonaws.services.iot.model.DescribeEndpointRequest
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.DeleteObjectsRequest
import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.AmazonSNSClient

class ApiReactor(
        val s3Client: AmazonS3 = AwsConfigurator.defaultClient(AmazonS3Client.builder()),
        val snsClient: AmazonSNS = AwsConfigurator.defaultClient(AmazonSNSClient.builder()),
        val iotClient: AWSIot = AwsConfigurator.defaultClient(AWSIotClient.builder()),
        val resourceBucket: String = System.getenv("BUCKET"),
        val loadTopic: String = System.getenv("LOAD_TOPIC")
): RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    override fun handleRequest(apiGatewayProxyRequestEvent: APIGatewayProxyRequestEvent, context: Context): APIGatewayProxyResponseEvent {
        val response = when(apiGatewayProxyRequestEvent.path){
            "/reset" -> handleReset()
            "/status" -> handleStatus()
            "/load" -> handleLoad()
            "/notificationEndpoint" -> handleNotificationEndpoint()
            else -> 400 to "NotFound"
        }
        return APIGatewayProxyResponseEvent().withStatusCode(response.first).withBody(response.second)
    }

    fun handleNotificationEndpoint(): Pair<Int, String>{
       return 200 to iotClient.describeEndpoint(DescribeEndpointRequest()).endpointAddress
    }

    fun handleLoad(): Pair<Int, String>{
        snsClient.publish(loadTopic, "Load")
        return 200 to "Loading"
    }

    fun handleReset(): Pair<Int, String>{
        println("Resetting")
        val keys = s3Client.listObjects(resourceBucket).objectSummaries.map { it.key }
        if(keys.isEmpty()){
            println("No Keys Found")
        }
        else {
            println("Deleting all objects")
            s3Client.deleteObjects(DeleteObjectsRequest(resourceBucket).withKeys(*keys.toTypedArray()))
        }
        return 200 to keys.fold(StringBuilder()){acc, it -> acc.append(it).append("\n")}.toString()
    }

    fun handleStatus(): Pair<Int, String>{
        val keys = s3Client.listObjects(resourceBucket).objectSummaries.map { it.key }
        val status = (if(keys.isEmpty()) "Not" else "") + "Started"
        val directories = if(keys.isNotEmpty()) {
            keys
            .filter { it.endsWith('/') }
            .map { it.substringBeforeLast('/') }
            .fold(StringBuilder()) { acc, it -> acc.append("\"$it\", ") }
            .toString().substringBeforeLast(',')
        } else ""
        return 200 to "{\"status\":\"$status\", \"keys\":[$directories]}"
    }
}