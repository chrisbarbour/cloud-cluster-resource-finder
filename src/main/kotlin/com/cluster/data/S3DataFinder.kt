package com.cluster.data

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3Client
import com.cluster.AwsConfigurator
import com.cluster.api.Account
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue


class S3DataFinder(
        private val s3Client: AmazonS3 = AwsConfigurator.defaultClient(AmazonS3Client.builder()),
        private val s3Bucket: String = System.getenv("BUCKET")
): DataFinder{

    override fun userInfoFor(username: String): Account.User = find(infoKeyForUser(username), orCreate = Account.User(username))
    override fun accountInfoFor(accountId: String): Account = find(infoKeyForAccount(accountId), orCreate = Account(accountId))

    override fun updateUserInfo(user: Account.User) { s3Client.putObject(s3Bucket, infoKeyForUser(user.username), jackson.writeValueAsString(user))}

    private inline fun <reified T: Any> find(key: String, orCreate: T): T{
        return if(!s3Client.doesObjectExist(s3Bucket, key)){
            val defaultAsString = jackson.writeValueAsString(orCreate)
            s3Client.putObject(s3Bucket, key, defaultAsString)
            orCreate
        }
        else jackson.readValue(s3Client.getObjectAsString(s3Bucket, key))
    }

    companion object {
        private val jackson = jacksonObjectMapper()
        private val infoJson = "info.json"
        private fun keyForUser(username: String) = "users/$username/"
        private fun keyForAccount(accountId: String) = "accounts/$accountId/"
        private fun infoKeyForUser(username: String) = keyForUser(username) + infoJson
        private fun infoKeyForAccount(accountId: String) = keyForAccount(accountId) + infoJson
    }
}