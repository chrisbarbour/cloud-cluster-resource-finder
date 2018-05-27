package com.cluster.api

import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProviderClient
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectMetadata
import com.cluster.AwsConfigurator
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.ByteArrayInputStream

class Accounts(
        private val cognitoClient: AWSCognitoIdentityProvider = AwsConfigurator.defaultClient(AWSCognitoIdentityProviderClient.builder()),
        private val s3Client: AmazonS3 = AwsConfigurator.defaultClient(AmazonS3Client.builder()),
        private val infoBucket: String = System.getenv("BUCKET"),
        private val userPoolId: String = "eu-west-1_4KfOFr5m3",
        override val resources: List<String> = listOf("/accounts")
): ApiReactor.ApiHandler{

    override fun handle(resource: String, username: String, event: APIGatewayProxyRequestEvent): APIGatewayProxyResponseEvent {
        return when(resource){
            "/accounts" -> getAccounts(username)
            else -> notHandledResource()
        }
    }

    fun accessInfo(username: String): List<Account>{
        val accessKey = "accounts/access.json"
        val accessObjectExists = s3Client.doesObjectExist(infoBucket, accessKey)
        return if(!accessObjectExists){
            val metadata = ObjectMetadata()
            metadata.contentType = "application/json"
            s3Client.putObject(infoBucket, accessKey, ByteArrayInputStream(jackson.writeValueAsBytes(Access())), metadata)
            emptyList()
        }
        else{
            val accessContent = s3Client.getObjectAsString(infoBucket, accessKey)
            val access = jackson.readValue<Access>(accessContent)
            val allGroups = access.groups
            val allAccounts = access.accounts
            val user = access.users.find { it.username == username }
            val groups = user?.groups.orEmpty()
            val aliases = user?.aliases.orEmpty()
            groups
                .mapNotNull{ group -> allGroups.find { it.name == group } }
                .flatMap { it.accounts }
                .mapNotNull { account -> allAccounts.find { it.id == account } }
                .map { account ->  aliases.find { it.id == account.id } ?: account }
        }
    }

    fun getAccounts(username: String): APIGatewayProxyResponseEvent{
        val accounts = accessInfo(username)
        return APIGatewayProxyResponseEvent().withStatusCode(200).withBody(
                jackson.writeValueAsString(accounts)
        )
    }

    fun notHandledResource() = APIGatewayProxyResponseEvent().withStatusCode(404)

    data class Access(val groups: List<Group> = emptyList(), val users: List<User> = emptyList(), val accounts: List<Account> = emptyList())
    data class Group(val name: String, val accounts: List<String> = emptyList())
    data class User(val username: String, val groups: List<String> = emptyList(), val aliases: List<Account> = emptyList())
    data class Account(val id: String, val name: String)

    companion object {
        private val jackson = jacksonObjectMapper()
    }
}