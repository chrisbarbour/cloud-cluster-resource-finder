package com.cluster

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.DeleteObjectsRequest
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.securitytoken.AWSSecurityTokenService
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest
import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.AmazonSNSClient
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.ByteArrayInputStream
import java.util.*

class ApiReactor(
        val s3Client: AmazonS3 = AwsConfigurator.defaultClient(AmazonS3Client.builder()),
        val snsClient: AmazonSNS = AwsConfigurator.defaultClient(AmazonSNSClient.builder()),
        val stsClient: AWSSecurityTokenService = AwsConfigurator.defaultClient(AWSSecurityTokenServiceClient.builder()),
        val resourceBucket: String = System.getenv("BUCKET"),
        val loadTopic: String = System.getenv("LOAD_TOPIC"),
        val iotRoleArn: String = System.getenv("IOT_ROLE_ARN"),
        val accountId: String = System.getenv("ACCOUNT"),
        val iotPublisher: IotPublisher = IotPublisher()
): RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    override fun handleRequest(apiGatewayProxyRequestEvent: APIGatewayProxyRequestEvent, context: Context): APIGatewayProxyResponseEvent {
        val response = when(apiGatewayProxyRequestEvent.path){
            "/reset" -> handleReset()
            "/status" -> handleStatus()
            "/load" -> handleLoad()
            "/iotAccess" -> iotAccess()
            "/notificationEndpoint" -> handleNotificationEndpoint()
            "/accounts" -> handleAccounts()
            "/account/{accountId}" -> handleAccount(apiGatewayProxyRequestEvent.pathParameters["accountId"]!!)
            else -> 400 to "NotFound"
        }
        return APIGatewayProxyResponseEvent()
                .withStatusCode(response.first)
                .withBody(response.second)
                .withHeaders(mapOf(
                        "Access-Control-Allow-Origin" to "*"
                ))
    }

    fun iotAccess(): Pair<Int, String>{
        val credentials = stsClient.assumeRole(
                AssumeRoleRequest()
                        .withRoleArn(iotRoleArn)
                        .withDurationSeconds(3600)
                        .withRoleSessionName(UUID.randomUUID().toString())
        ).credentials
        return 200 to "{\"AWS_ACCESS_KEY_ID\":\"${credentials.accessKeyId}\", \"AWS_SECRET_ACCESS_KEY\":\"${credentials.secretAccessKey}\", \"AWS_SESSION_TOKEN\":\"${credentials.sessionToken}\"}"
    }

    fun handleNotificationEndpoint(): Pair<Int, String>{
       return 200 to iotPublisher.endpoint()
    }

    fun handleLoad(): Pair<Int, String>{
        return if(s3Client.listObjects(resourceBucket).objectSummaries.map { it.key }.isEmpty()) {
            s3Client.putObject(resourceBucket, "loading", ByteArrayInputStream("".toByteArray()), ObjectMetadata())
            iotPublisher.postEvent("loading")
            snsClient.publish(loadTopic, "Load")
            200 to "Loading"
        }
        else 200 to "Already Loading"
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
            iotPublisher.postEvent("reset")
        }
        return 200 to keys.fold(StringBuilder()){acc, it -> acc.append(it).append("\n")}.toString()
    }
    fun resourceKeys(): List<String> = s3Client.listObjects(resourceBucket).objectSummaries.map { it.key }
    fun loadedTypes(keys: List<String>): List<String> =
        keys.map{ it.substringBefore('/')}.toSet().filter { it != "loading" }

    fun handleStatus(): Pair<Int, String>{
        val resourceKeys = resourceKeys()
        val loaded = loadedTypes(resourceKeys)
        fun listString(list: Collection<*>) = list.foldIndexed(StringBuilder()){i, acc, it -> acc.append("\"$it\"").append(if(i<list.size-1){", "}else {""})}.toString()
        val toLoad = listOf("lambda", "ec2").filter { !loaded.contains(it) }
        val status = if(toLoad.isEmpty()){"Finished"}else((if(resourceKeys.isEmpty()) "Not" else "") + "Started")
        return 200 to "{\"status\":\"$status\", \"loaded\":[${listString(loaded)}], \"waitingOn\":[${listString(toLoad)}]}"
    }

    fun handleAccounts(): Pair<Int, String>{
        return 200 to "[\"$accountId\"]"
    }

    fun handleAccount(account: String): Pair<Int, String>{
        val resources = loadedTypes(resourceKeys())
        .map { s3Client.getObject(resourceBucket, "$it/info.json").objectContent.bufferedReader().readText() }
        .flatMap { jsonReader.readValue<List<AwsResource>>(it) }
        return 200 to jsonWriter.writeValueAsString(resources)
    }

    companion object {
        val jsonWriter = jacksonObjectMapper().writerWithDefaultPrettyPrinter()
        val jsonReader = jacksonObjectMapper().reader()
    }
}