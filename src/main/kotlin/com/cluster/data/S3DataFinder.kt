package com.cluster.data

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectMetadata
import com.cluster.AwsConfigurator
import com.cluster.api.Account
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.ByteArrayInputStream


class S3DataFinder(
        private val s3Client: AmazonS3 = AwsConfigurator.defaultClient(AmazonS3Client.builder()),
        private val s3Bucket: String = System.getenv("BUCKET")
): DataFinder{

    override fun userInfoFor(username: String): Account.User = find(infoKeyForUser(username), orCreate = Account.User(username))
    override fun accountInfoFor(accountId: String): Account = find(infoKeyForAccount(accountId), orCreate = Account(accountId))

    override fun updateUserInfo(user: Account.User) { s3Client.putObject(s3Bucket, infoKeyForUser(user.username), jackson.writeValueAsString(user))}

    override fun updateAccountInfoFor(accountId: String, resource: String, info: Any) {
        s3Client.putObject(s3Bucket, keyForAccount(accountId) + "$resource/info.json", jackson.writeValueAsString(info).asStream(),jsonMeta())
        val keys = s3Client.listObjects(s3Bucket, keyForAccount(accountId)).objectSummaries.map { it.key.substring(keyForAccount(accountId).length).substringBefore("/info.json") }
        println("keys: " + keys)
        val currentAccountInfo = accountInfoFor(accountId)
        if(keys.contains("lambda")){
            val newInfo = currentAccountInfo.copy(initialized = true, loading = false)
            s3Client.putObject(s3Bucket, infoKeyForAccount(accountId), jackson.writeValueAsString(newInfo).asStream(),jsonMeta())
        }
    }

    private fun String.asStream() = ByteArrayInputStream(this.toByteArray())

    private inline fun <reified T: Any> find(key: String, orCreate: T): T{
        return if(!s3Client.doesObjectExist(s3Bucket, key)){
            val defaultAsString = jackson.writeValueAsString(orCreate)
            s3Client.putObject(s3Bucket, key, defaultAsString.asStream(),jsonMeta())
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
        private fun jsonMeta(): ObjectMetadata{
            val meta = ObjectMetadata()
            meta.contentType = "application/json"
            return meta
        }
    }
}