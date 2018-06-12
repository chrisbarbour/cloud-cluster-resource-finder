package com.cluster.api

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import com.cluster.data.DataFinder
import com.cluster.data.S3DataFinder
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

class AccountHandler(private val dataFinder: DataFinder = S3DataFinder()): ApiReactor.ApiGatewayHandler {

    override fun resources() = mapOf(
            "/accounts/{$ACCOUNT_ID}/alias/{$ALIAS}" to mapOf("PUT" to ::addAlias, "DELETE" to ::deleteAlias)
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


    companion object {
        private const val ACCOUNT_ID = "accountId"
        private const val ALIAS = "alias"
        private val jackson = jacksonObjectMapper()
    }
}