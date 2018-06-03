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
import java.util.*

class Accounts(
        private val s3Client: AmazonS3 = AwsConfigurator.defaultClient(AmazonS3Client.builder()),
        private val infoBucket: String = System.getenv("BUCKET"),
       override val resources: List<String> = listOf("/accounts", "/accounts/{accountId}")
): ApiReactor.ApiHandler{

    override fun handle(resource: String, username: String, event: APIGatewayProxyRequestEvent): APIGatewayProxyResponseEvent {
        println("Method -> ${event.httpMethod}")
        return when(resource){
            "/accounts" -> getAccounts(username)
            "/accounts/{accountId}" -> when(event.httpMethod.toUpperCase()){
                "GET" -> getAccount(username, event.pathParameters["accountId"]!!)
                "PUT" -> putAccount(username, event.pathParameters["accountId"]!!, event.body)
                else -> notHandledResource()
            }
            else -> notHandledResource()
        }
    }

    fun putAccessInfo(access: Access){
        val accessKey = "accounts/access.json"
        val metadata = ObjectMetadata()
        metadata.contentType = "application/json"
        s3Client.putObject(infoBucket, accessKey, ByteArrayInputStream(jackson.writeValueAsBytes(access)), metadata)
    }

    fun fullAccessInfo(): Access{
        val accessKey = "accounts/access.json"
        val accessObjectExists = s3Client.doesObjectExist(infoBucket, accessKey)
        return if(!accessObjectExists){
            val access = Access()
            putAccessInfo(access)
            access
        }
        else {
            val accessContent = s3Client.getObjectAsString(infoBucket, accessKey)
            jackson.readValue(accessContent)
        }
    }

    fun accessInfoFor(username: String, from: Access): List<Account>{
        val allGroups = from.groups
        val allAccounts = from.accounts
        val user = from.users.find { it.username == username }
        val groups = user?.groups.orEmpty()
        val aliases = user?.aliases.orEmpty()
        return groups
            .mapNotNull{ group -> allGroups.find { it.name == group } }
            .flatMap { it.accounts }
            .mapNotNull { account -> allAccounts.find { it.id == account } }
            .map { account ->  aliases.find { it.id == account.id } ?: account }
    }

    fun getAccounts(username: String): APIGatewayProxyResponseEvent{
        val accounts = accessInfoFor(username, fullAccessInfo())
        return APIGatewayProxyResponseEvent().withStatusCode(200).withBody(
                jackson.writeValueAsString(accounts)
        )
    }

    fun putAccount(username: String, accountId: String, body: String): APIGatewayProxyResponseEvent{
        val access = fullAccessInfo()
        val accountName = jackson.readValue<AccountNameOnly>(body).name
        val user = access.users.find { it.username == username }
        val newGroup = Group(UUID.randomUUID().toString(), listOf(accountId))
        val account = access.accounts.find { it.id == accountId }
        val newAccess = if(account != null){
            val aliases = if(account.name != accountName) listOf(Account(accountId, accountName)) else emptyList()
            if(user != null){
                val updatedUser = User(username, user.groups + newGroup.name, user.aliases + aliases)
                val newUsers = access.users.filter { it.username != username } + updatedUser
                Access(access.groups + newGroup, newUsers, access.accounts)
            }
            else{
                val newUser = User(username, listOf(newGroup.name), aliases)
                Access(access.groups + newGroup, access.users + newUser, access.accounts)
            }
        }
        else{
            val newAccount = Account(accountId, accountName)
            if(user != null){
                val updatedUser = User(username, user.groups + newGroup.name, user.aliases)
                val newUsers = access.users.filter { it.username != username } + updatedUser
                Access(access.groups + newGroup, newUsers, access.accounts + newAccount)
            }
            else{
                val newUser = User(username, listOf(newGroup.name))
                Access(access.groups + newGroup, access.users + newUser, access.accounts + newAccount)
            }
        }
        putAccessInfo(newAccess)
        val responseBody = jackson.writeValueAsString(Account(accountId, accountName))
        return if(account != null)
            APIGatewayProxyResponseEvent()
                    .withHeaders( mapOf("Content-Type" to "application/json"))
                    .withBody(responseBody)
                    .withStatusCode(200)
         else APIGatewayProxyResponseEvent()
                .withHeaders(mapOf("Content-Type" to "application/json", "Location" to "/accounts/$accountId"))
                .withBody(responseBody)
                .withStatusCode(201)
    }

    fun getAccount(username: String, accountId: String): APIGatewayProxyResponseEvent{
        val accessibleAccounts = accessInfoFor(username, fullAccessInfo())
        if(accessibleAccounts.find { it.id == accountId } != null){
           val status = if(s3Client.doesObjectExist(infoBucket, "/accounts/$accountId/status.json")){
               val payload = s3Client.getObjectAsString(infoBucket, "/accounts/$accountId/status.json")
               jackson.readValue(payload)
           }
           else{
               val status = AccountStatus()
               val payload = jackson.writeValueAsBytes(status)
               val metadata = ObjectMetadata()
               metadata.contentType = "application/json"
               s3Client.putObject(infoBucket, "/accounts/$accountId/status.json",ByteArrayInputStream(payload),metadata)
               status
           }
            val responseBody = jackson.writeValueAsString(status)
            return APIGatewayProxyResponseEvent()
                    .withHeaders( mapOf("Content-Type" to "application/json"))
                    .withBody(responseBody)
                    .withStatusCode(200)
        }
        return APIGatewayProxyResponseEvent().withStatusCode(403)
    }

    fun notHandledResource() = APIGatewayProxyResponseEvent().withStatusCode(404)

    data class Access(val groups: List<Group> = emptyList(), val users: List<User> = emptyList(), val accounts: List<Account> = emptyList())
    data class Group(val name: String, val accounts: List<String> = emptyList())
    data class User(val username: String, val groups: List<String> = emptyList(), val aliases: List<Account> = emptyList())
    data class Account(val id: String, val name: String)
    data class AccountNameOnly(val name: String)

    data class AccountStatus(val initialized: Boolean = false,
                             val isLoading: Boolean = false,
                             val loaded: List<String> = emptyList(),
                             val loading: List<String> = emptyList())
    companion object {
        private val jackson = jacksonObjectMapper()
    }
}