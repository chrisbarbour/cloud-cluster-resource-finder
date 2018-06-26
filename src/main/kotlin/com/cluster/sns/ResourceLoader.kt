package com.cluster.sns

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.client.builder.AwsSyncClientBuilder
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient
import com.amazonaws.services.lambda.AWSLambdaClient
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.SNSEvent
import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.AmazonSNSClient
import com.amazonaws.services.sns.AmazonSNSClientBuilder
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
            val resources = resourcesIn("eu-west-1", request.resource, request.accountId, request.credentials)
            dataFinder.updateAccountInfoFor(request.accountId, request.resource, request.username, resources)
        }
        catch(e: Exception){
            System.err.println("Error, cannot show in case it is sensitive")
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
        else -> throw IllegalArgumentException()
    }
    return finder.findIn(accountId, listOf(region))
}

private fun <T: AwsClientBuilder<*,*>, R> client(builder: AwsClientBuilder<T,R>, credentials: AWSCredentialsProvider): (String) -> R = { region -> builder.also { it.credentials = credentials; it.region = region }.build() }