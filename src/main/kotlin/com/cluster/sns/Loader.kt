package com.cluster.sns

import com.amazonaws.services.kms.AWSKMS
import com.amazonaws.services.kms.AWSKMSClient
import com.amazonaws.services.kms.model.DecryptRequest
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.SNSEvent
import com.cluster.AwsConfigurator
import com.cluster.api.Account
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.ByteBuffer
import java.nio.charset.Charset

class Loader(private val kmsClient: AWSKMS = AwsConfigurator.defaultClient(AWSKMSClient.builder()))
    : RequestHandler<SNSEvent, String>{

    override fun handleRequest(event: SNSEvent, context: Context?): String {
        System.out.println(event.records[0].sns.message[0])
        System.out.println(event.records[0].sns.message.toByteArray()[0])
        System.out.println(String(event.records[0].sns.message.toByteArray()))
        System.out.println(String(event.records[0].sns.message.toByteArray(), Charset.defaultCharset()))
        val authString = kmsClient.decrypt(DecryptRequest().withCiphertextBlob(ByteBuffer.wrap(event.records[0].sns.message.toByteArray()))).plaintext
        val auth = jackson.readValue<Account.AwsAuth>(String(authString.array(), Charset.defaultCharset()))
        System.out.println(auth.awsAccessKeyId)
        return "Done"
    }

    companion object {
        private val jackson = jacksonObjectMapper()
    }

}