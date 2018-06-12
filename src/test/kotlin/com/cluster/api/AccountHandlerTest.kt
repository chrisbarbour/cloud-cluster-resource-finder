package com.cluster.api

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.cluster.data.DataFinder
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import org.junit.Test
import org.mockito.Mockito
import org.mockito.internal.verification.VerificationModeFactory
import kotlin.test.expect

class AccountHandlerTest{

    private val dataFinder: DataFinder = mock()

    @Test
    fun `should append alias when adding account alias`(){
        val username = "bob"
        val addedAlias =  Account.Alias("5432", "Non Prod")
        val userBefore = Account.User(username, listOf(Account.Alias("1234", "Sandbox")))
        val userAfter = Account.User(username, listOf(Account.Alias("1234", "Sandbox"), addedAlias))
        val event = ApiReactor.AuthorizedEvent(username, APIGatewayProxyRequestEvent().withPathParamters(mapOf("accountId" to addedAlias.accountId, "alias" to addedAlias.name )))
        Mockito.`when`(dataFinder.userInfoFor(username)).thenReturn(userBefore)
        expect(userAfter){ jacksonObjectMapper().readValue(AccountHandler(dataFinder).addAlias(event).body) }
        Mockito.verify(dataFinder).updateUserInfo(userAfter)
    }

    @Test
    fun `should not append alias when adding account alias and id already has alias`(){
        val username = "bob"
        val addedAlias =  Account.Alias("1234", "Non Prod")
        val userBefore = Account.User(username, listOf(Account.Alias("1234", "Sandbox")))
        val userAfter = Account.User(username, listOf(Account.Alias("1234", "Sandbox")))
        val event = ApiReactor.AuthorizedEvent(username, APIGatewayProxyRequestEvent().withPathParamters(mapOf("accountId" to addedAlias.accountId, "alias" to addedAlias.name )))
        Mockito.`when`(dataFinder.userInfoFor(username)).thenReturn(userBefore)
        expect(userAfter){ jacksonObjectMapper().readValue(AccountHandler(dataFinder).addAlias(event).body) }
        Mockito.verify(dataFinder,VerificationModeFactory.times(0)).updateUserInfo(userAfter)
    }

    @Test
    fun `should remove alias when deleting account alias`(){
        val username = "bob"
        val removedAlias =  Account.Alias("5432", "Non Prod")
        val userBefore = Account.User(username, listOf(Account.Alias("1234", "Sandbox"), removedAlias))
        val userAfter = Account.User(username, listOf(Account.Alias("1234", "Sandbox")))
        val event = ApiReactor.AuthorizedEvent(username, APIGatewayProxyRequestEvent().withPathParamters(mapOf("accountId" to removedAlias.accountId, "alias" to removedAlias.name )))
        Mockito.`when`(dataFinder.userInfoFor(username)).thenReturn(userBefore)
        expect(userAfter){ jacksonObjectMapper().readValue(AccountHandler(dataFinder).deleteAlias(event).body) }
        Mockito.verify(dataFinder).updateUserInfo(userAfter)

    }

}