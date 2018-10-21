package com.cluster.sns

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.apigateway.AmazonApiGatewayClient
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ecs.AmazonECSClient
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient
import com.amazonaws.services.kms.AWSKMSClient
import com.amazonaws.services.lambda.AWSLambdaClient
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.SNSEvent
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.sns.AmazonSNSClient
import com.amazonaws.services.sqs.AmazonSQSClient
import com.cluster.*
import com.cluster.api.Account
import com.cluster.data.DataFinder
import com.cluster.data.S3DataFinder
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

class ResourceLoader(
        private val kmsClient: KmsClient = KmsClient(),
        private val dataFinder: DataFinder = S3DataFinder()
): RequestHandler<SNSEvent, String>{

    override fun handleRequest(event: SNSEvent, context: Context?): String {
        try {
            val authString = kmsClient.decrypt(event.records[0].sns.message)
            val request = jackson.readValue<Account.ResourceLoaderRequest>(authString)
            val resources = resourcesIn(request.region, request.resource, request.accountId, request.credentials)
            dataFinder.updateAccountInfoFor(request.accountId, request.resource, request.username, resources)
        }
        catch(e: Exception){
            e.printStackTrace()
        }
        return "Done"
    }

    companion object {
        private val jackson = jacksonObjectMapper()
    }

}

fun resourcesIn(region: String, loader: String, accountId: String, credentials: Account.AwsAuth): List<AwsResource.Relationships>{
    val creds = AWSStaticCredentialsProvider(BasicAWSCredentials(credentials.awsAccessKeyId, credentials.awsSecretAccessKey))
    val finder: AwsResource.Finder = when(loader){
        "lambda" -> AwsResourceFinderLambda(client(AWSLambdaClient.builder(),creds))
        "iam" -> AwsResourceFinderIAM(client(AmazonIdentityManagementClient.builder(), creds))
        "sns" -> AwsResourceFinderSNS(client(AmazonSNSClient.builder(), creds))
        "s3" -> AwsResourceFinderS3(client(AmazonS3Client.builder(), creds))
        "api" -> AwsResourceFinderAPIGateway(client(AmazonApiGatewayClient.builder(), creds))
        "dynamo" -> AwsResourceFinderDynamoDB(client(AmazonDynamoDBClient.builder(), creds))
        "ec2" -> AwsResourceFinderEC2(client(AmazonEC2Client.builder(), creds))
        "ecs" -> AwsResourceFinderECS(client(AmazonECSClient.builder(), creds))
        "kms" -> AwsResourceFinderKMS(client(AWSKMSClient.builder(), creds))
        "sqs" -> AwsResourceFinderSQS(client(AmazonSQSClient.builder(), creds))
        else -> throw IllegalArgumentException()
    }
    return finder.findIn(accountId, listOf(region))
}

private fun <T: AwsClientBuilder<*,*>, R> client(builder: AwsClientBuilder<T,R>, credentials: AWSCredentialsProvider): (String) -> R = { region -> builder.also { it.credentials = credentials; it.region = region }.build() }