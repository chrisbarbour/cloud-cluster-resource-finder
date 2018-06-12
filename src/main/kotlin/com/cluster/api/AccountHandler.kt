package com.cluster.api

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import com.cluster.AccessChecker
import com.cluster.data.DataFinder
import com.cluster.data.S3DataFinder
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

class AccountHandler(
        private val dataFinder: DataFinder = S3DataFinder(),
        private val loadTopic: String = System.getenv("LOAD_TOPIC"),
        private val accessChecker: AccessChecker = AccessChecker()
): ApiReactor.ApiGatewayHandler {

    override fun resources() = mapOf(
            "/accounts/{$ACCOUNT_ID}/alias/{$ALIAS}" to mapOf("PUT" to ::addAlias, "DELETE" to ::deleteAlias),
            "/accounts/{$ACCOUNT_ID}/load" to mapOf("POST" to ::load)
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
        val hasAccess = verifyAccess(event.payload.pathParameters[ACCOUNT_ID]!!, creds)
        return if(hasAccess) APIGatewayProxyResponseEvent().withBody("User has Access!").withStatusCode(200)
        else APIGatewayProxyResponseEvent().withStatusCode(403)
    }

    fun verifyAccess(accountId: String, credentials: Account.AwsAuth): Boolean{
        return accessChecker.userHasAccess(accountId, credentials)
    }

    companion object {
        private const val ACCOUNT_ID = "accountId"
        private const val ALIAS = "alias"
        private val jackson = jacksonObjectMapper()
    }
}