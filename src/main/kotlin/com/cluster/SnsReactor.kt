package com.cluster

import com.amazonaws.services.iot.AWSIot
import com.amazonaws.services.iot.AWSIotClient
import com.amazonaws.services.iot.client.AWSIotMqttClient
import com.amazonaws.services.iot.client.AWSIotQos
import com.amazonaws.services.iot.model.DescribeEndpointRequest
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.SNSEvent
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectMetadata
import java.io.ByteArrayInputStream
import java.util.*

class SnsReactor(
        val s3Client: AmazonS3 = AwsConfigurator.defaultClient(AmazonS3Client.builder()),
        val iotClient: AWSIot = AwsConfigurator.defaultClient(AWSIotClient.builder()),
        val resourceBucket: String = System.getenv("BUCKET"),
        val resourceType: String = System.getenv("RESOURCE"),
        val accessKey: String = System.getenv("AWS_ACCESS_KEY_ID"),
        val secretKey: String = System.getenv("AWS_SECRET_ACCESS_KEY"),
        val sessionToken: String = System.getenv("AWS_SESSION_TOKEN")
): RequestHandler<SNSEvent, String> {
    override fun handleRequest(event: SNSEvent, context: Context): String {
        val iotEndpoint = iotClient.describeEndpoint(DescribeEndpointRequest()).endpointAddress
        when(resourceType){
            "lambda" -> lambda(iotEndpoint)
        }
        return "Done"
    }

    fun postEvent(iotEndpoint: String, event: String){
        val client = AWSIotMqttClient(iotEndpoint, UUID.randomUUID().toString(), accessKey, secretKey, sessionToken)
        client.connect(2000)
        client.publish("loadInfo", AWSIotQos.QOS0, event)
        client.disconnect()
    }

    fun lambda(iotEndpoint: String){
        val lambdas = AwsResourceFinderLambda().lambdaResources("eu-west-1", "account").map { it.resource.arn }
        val lambdaInfo = lambdas.fold(StringBuilder()){acc, it -> acc.append(it).append("\n")}.toString()
        s3Client.putObject(resourceBucket,resourceType + "/info.json",ByteArrayInputStream(lambdaInfo.toByteArray()), ObjectMetadata())
        postEvent(iotEndpoint, resourceType)
    }

}