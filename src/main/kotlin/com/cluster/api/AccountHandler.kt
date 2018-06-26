package com.cluster.api

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.AmazonSNSClient
import com.amazonaws.services.sns.model.PublishRequest
import com.cluster.AccessChecker
import com.cluster.AwsConfigurator
import com.cluster.KmsClient
import com.cluster.data.DataFinder
import com.cluster.data.S3DataFinder
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

class AccountHandler(
        private val dataFinder: DataFinder = S3DataFinder(),
        private val loadTopic: String = System.getenv("LOAD_TOPIC"),
        private val accessChecker: AccessChecker = AccessChecker(),
        private val kmsClient: KmsClient = KmsClient(),
        private val snsClient: AmazonSNS = AwsConfigurator.defaultClient(AmazonSNSClient.builder())
): ApiReactor.ApiGatewayHandler {

    override fun resources() = mapOf(
            "/accounts/{$ACCOUNT_ID}/alias/{$ALIAS}" to mapOf("PUT" to ::addAlias, "DELETE" to ::deleteAlias),
            "/accounts/{$ACCOUNT_ID}/load" to mapOf("POST" to ::load),
            "/accounts/{$ACCOUNT_ID}" to mapOf("GET" to ::accountData)
    )

    private fun updateAlias(event: ApiReactor.AuthorizedEvent, update: (aliases: List<Account.Alias>) -> List<Account.Alias>): APIGatewayProxyResponseEvent {
        val userInfo = dataFinder.userInfoFor(event.username)
        val updatedUserInfo = userInfo.copy(accountAliases = update(userInfo.accountAliases))
        if(updatedUserInfo != userInfo) {
            dataFinder.updateUserInfo(updatedUserInfo)
        }
        return APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withBody(jackson.writeValueAsString(updatedUserInfo))
    }

    private fun aliasFrom(event:  ApiReactor.AuthorizedEvent) = Account.Alias(event.payload.pathParameters[ACCOUNT_ID]!!, event.payload.pathParameters[ALIAS]!!)
    private fun aliasExists(aliases: List<Account.Alias>, accountId: String) = aliases.find { it.accountId == accountId } != null

    fun addAlias(event: ApiReactor.AuthorizedEvent) = updateAlias(event) { aliases ->
        if(!aliasExists(aliases, event.payload.pathParameters[ACCOUNT_ID]!!)) aliases + aliasFrom(event)
        else aliases
    }
    fun deleteAlias(event: ApiReactor.AuthorizedEvent) = updateAlias(event) { it - aliasFrom(event) }

    fun load(event: ApiReactor.AuthorizedEvent): APIGatewayProxyResponseEvent{
        val creds: Account.AwsAuth = jackson.readValue(event.payload.body)
        val accountId = event.payload.pathParameters[ACCOUNT_ID]!!
        val hasAccess = verifyAccess(accountId, creds)
        return if(hasAccess){
            resources.forEach {
                val request = Account.ResourceLoaderRequest(accountId, it, event.username, creds)
                val encryptedCreds = kmsClient.encrypt(jackson.writeValueAsString(request))
                snsClient.publish(PublishRequest().withTopicArn(loadTopic).withMessage(encryptedCreds))
            }
            APIGatewayProxyResponseEvent().withBody("User has Access, Loading resources: $resources").withStatusCode(200)
        }
        else APIGatewayProxyResponseEvent().withStatusCode(403)
    }

    fun verifyAccess(accountId: String, credentials: Account.AwsAuth): Boolean{
        return credentials.awsAccessKeyId.isNotEmpty() && accessChecker.userHasAccess(accountId, credentials)
    }

    fun accountData(event: ApiReactor.AuthorizedEvent): APIGatewayProxyResponseEvent{
        val accountId = event.payload.pathParameters[ACCOUNT_ID]!!
        val accountInfo = dataFinder.accountInfoFor(accountId)
        val accessLevel = accountInfo.accessLevelFor(event.username)
        if(accessLevel == Account.AccessLevel.VIEW || accessLevel == Account.AccessLevel.ADMIN){
            if(accountInfo.initialized && !accountInfo.loading){
                return APIGatewayProxyResponseEvent().withStatusCode(200).withBody(dataFinder.accountTreeFor(accountId))
            }
            else{
                return APIGatewayProxyResponseEvent().withStatusCode(400)
            }
        }
        else return APIGatewayProxyResponseEvent().withStatusCode(403)
    }

    companion object {
        val resources = listOf("lambda", "iam")
        private const val ACCOUNT_ID = "accountId"
        private const val ALIAS = "alias"
        private val jackson = jacksonObjectMapper()
    }
}