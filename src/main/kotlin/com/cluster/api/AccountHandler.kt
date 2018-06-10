package com.cluster.api

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import com.cluster.data.DataFinder
import com.cluster.data.S3DataFinder
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

class AccountHandler(val dataFinder: DataFinder = S3DataFinder()): ApiReactor.ApiGatewayHandler {

    override fun resources() = mapOf(
            "/accounts/{$ACCOUNT_ID}/alias/{$ALIAS}" to mapOf("PUT" to ::addAlias, "DELETE" to ::deleteAlias)
    )

    fun updateAlias(event: ApiReactor.AuthorizedEvent, update: (aliases: List<Account.Alias>) -> List<Account.Alias>): APIGatewayProxyResponseEvent {
        val updatedUserInfo = with(dataFinder.userInfoFor(event.username)){ this.copy(accountAliases = update(this.accountAliases)) }
        dataFinder.updateUserInfo(updatedUserInfo)
        return APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withBody(jackson.writeValueAsString(updatedUserInfo))
    }

    fun aliasFrom(event:  ApiReactor.AuthorizedEvent) = Account.Alias(event.payload.pathParameters[ACCOUNT_ID]!!, event.payload.pathParameters[ALIAS]!!)

    fun addAlias(event: ApiReactor.AuthorizedEvent) = updateAlias(event) { it + aliasFrom(event) }
    fun deleteAlias(event: ApiReactor.AuthorizedEvent) = updateAlias(event) { it - aliasFrom(event) }


    companion object {
        private const val ACCOUNT_ID = "accountId"
        private const val ALIAS = "alias"
        private val jackson = jacksonObjectMapper()
    }
}