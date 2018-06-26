package com.cluster.data

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectMetadata
import com.cluster.AwsConfigurator
import com.cluster.AwsResource
import com.cluster.Node
import com.cluster.NodeBuilder
import com.cluster.api.Account
import com.cluster.api.AccountHandler
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

    override fun updateAccountInfoFor(accountId: String, resource: String, username: String, info: Any) {
        s3Client.putObject(s3Bucket, keyForAccount(accountId) + "$resource/info.json", jackson.writeValueAsString(info).asStream(),jsonMeta())
        val keys = s3Client.listObjects(s3Bucket, keyForAccount(accountId)).objectSummaries.map { it.key.substring(keyForAccount(accountId).length).substringBefore("/info.json") }
        val currentAccountInfo = accountInfoFor(accountId)
        if(keys.containsAll(AccountHandler.resources)){
            val newMeta = (currentAccountInfo.info ?: Account.Meta()).let { it.copy(admins = it.admins + username) }
            val newInfo = currentAccountInfo.copy(initialized = true, loading = false,info = newMeta)
            s3Client.putObject(s3Bucket, infoKeyForAccount(accountId), jackson.writeValueAsString(newInfo).asStream(),jsonMeta())
            combineResources(accountId)
        }
    }

    fun combineResources(accountId: String){
        val resources =AccountHandler.resources.flatMap {
            jackson.readValue<List<AwsResource.Relationships>>(s3Client.getObjectAsString(s3Bucket, keyForAccount(accountId) + "$it/info.json"))
        }
        val nodeTree = NodeBuilder.buildFrom(resources)
        s3Client.putObject(s3Bucket, nodeKeyForAccount(accountId), jackson.writeValueAsString(nodeTree).asStream(), jsonMeta())
    }

    override fun accountTreeFor(accountId: String) = s3Client.getObjectAsString(s3Bucket, nodeKeyForAccount(accountId))

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
        private fun nodeKeyForAccount(accountId: String) = keyForAccount(accountId) + "nodeTree.json"
        private fun jsonMeta() = ObjectMetadata().also { it.contentType = "application/json" }
    }
}