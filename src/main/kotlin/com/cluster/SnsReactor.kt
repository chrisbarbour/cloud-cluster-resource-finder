package com.cluster

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.SNSEvent
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectMetadata
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.ByteArrayInputStream

class SnsReactor(
        val s3Client: AmazonS3 = AwsConfigurator.defaultClient(AmazonS3Client.builder()),
        val resourceBucket: String = System.getenv("BUCKET"),
        val accountId: String = System.getenv("ACCOUNT"),
        val resourceType: String = System.getenv("RESOURCE"),
        val iotPublisher: IotPublisher = IotPublisher()
): RequestHandler<SNSEvent, String> {
    override fun handleRequest(event: SNSEvent, context: Context): String {
        when(resourceType){
            "lambda" -> lambda()
            "ec2" -> ec2()
        }
        return "Done"
    }

    fun lambda(){
        val lambdas = AwsResourceFinderLambda().lambdaResources("eu-west-1", accountId)
        s3Client.putObject(
                resourceBucket,
                resourceType + "/info.json",
                ByteArrayInputStream(jsonWriter.writeValueAsBytes(lambdas)),
                ObjectMetadata()
        )
        iotPublisher.postEvent(resourceType)
    }

    fun ec2(){
        val ec2s = AwsResourceFinderEC2().ec2Resources("eu-west-1", "account").map { it.resource.arn }
        val ec2Info = ec2s.fold(StringBuilder()){acc, it -> acc.append(it).append("\n")}.toString()
        s3Client.putObject(resourceBucket,resourceType + "/info.json",ByteArrayInputStream(ec2Info.toByteArray()), ObjectMetadata())
        iotPublisher.postEvent(resourceType)
    }

    companion object {
        val jsonWriter = jacksonObjectMapper().writerWithDefaultPrettyPrinter()
    }
}