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
        val resourceBucket: String = System.getenv("BUCKET"),
        val resourceType: String = System.getenv("RESOURCE"),
        val iotPublisher: IotPublisher = IotPublisher()
): RequestHandler<SNSEvent, String> {
    override fun handleRequest(event: SNSEvent, context: Context): String {
        when(resourceType){
            "lambda" -> lambda()
            "ec2" -> lambda()
        }
        return "Done"
    }

    fun lambda(){
        val lambdas = AwsResourceFinderLambda().lambdaResources("eu-west-1", "account").map { it.resource.arn }
        val lambdaInfo = lambdas.fold(StringBuilder()){acc, it -> acc.append(it).append("\n")}.toString()
        s3Client.putObject(resourceBucket,resourceType + "/info.json",ByteArrayInputStream(lambdaInfo.toByteArray()), ObjectMetadata())
        iotPublisher.postEvent(resourceType)
    }

}