package com.cluster.data

import com.amazonaws.services.s3.AmazonS3
import com.cluster.api.Account
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.nhaarman.mockito_kotlin.mock
import org.junit.Test
import org.mockito.Mockito
import kotlin.test.expect

class S3DataFinderTest {

    private var s3Client: SimpleS3 = mock()
    private var testBucket = "testBucket"
    private var dataFinder = S3DataFinder(s3Client, testBucket)

    @Test
    fun `should return default when user doesn't exist in bucket`(){
        val username = "Testy"
        val key = "users/$username/info.json"
        Mockito.`when`(s3Client.doesObjectExist(testBucket, key)).thenReturn(false)
        val expectedUser = Account.User(username)
        val userAsString = jacksonObjectMapper().writeValueAsString(expectedUser)
        expect(expectedUser){ dataFinder.userInfoFor(username) }
        //Mockito.verify(s3Client).putObject(testBucket, key, userAsString)
    }

    @Test
    fun `should return user in s3 when user exists in bucket`(){
        val username = "Testy"
        val expectedUser = Account.User(username, listOf(Account.Alias("abcdef123", name = "Sandbox")))
        val key = "users/$username/info.json"
        val userAsString = jacksonObjectMapper().writeValueAsString(expectedUser)
        Mockito.`when`(s3Client.doesObjectExist(testBucket, key)).thenReturn(true)
        Mockito.`when`(s3Client.getObjectAsString(testBucket, key)).thenReturn(userAsString)
        expect(expectedUser){ dataFinder.userInfoFor(username) }
    }

    @Test
    fun `should return default when account doesn't exist in bucket`(){
        val accountId = "TestAccount"
        Mockito.`when`(s3Client.doesObjectExist(testBucket, "accounts/$accountId/info.json")).thenReturn(false)
        val expectedAccount = Account(accountId)
        expect(expectedAccount){ dataFinder.accountInfoFor(accountId) }
    }

    @Test
    fun `should return account in s3 when account exists in bucket`(){
        val accountId = "TestAccount"
        val expectedAccount = Account(accountId,initialized = true, loading = true, info = Account.Meta(admins = listOf("One"), viewers = listOf("Two", "Three")))
        val key = "accounts/$accountId/info.json"
        Mockito.`when`(s3Client.doesObjectExist(testBucket, key)).thenReturn(true)
        Mockito.`when`(s3Client.getObjectAsString(testBucket, key)).thenReturn(jacksonObjectMapper().writeValueAsString(expectedAccount))
        expect(expectedAccount){ dataFinder.accountInfoFor(accountId) }
    }
}