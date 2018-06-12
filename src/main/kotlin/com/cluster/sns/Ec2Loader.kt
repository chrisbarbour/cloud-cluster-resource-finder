package com.cluster.sns

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.SNSEvent
import com.cluster.KmsClient
import com.cluster.api.Account
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

class Ec2Loader(private val kmsClient: KmsClient = KmsClient())
    : RequestHandler<SNSEvent, String>{

    override fun handleRequest(event: SNSEvent, context: Context?): String {
        try {
            val authString = kmsClient.decrypt(event.records[0].sns.message)
            val auth = jackson.readValue<Account.AwsAuth>(authString)
            System.out.println(auth.awsAccessKeyId)
        }
        catch(e: Exception){
            System.err.println("Error, cannot show in case it is sensitive")
        }
        return "Done"
    }

    companion object {
        private val jackson = jacksonObjectMapper()
    }

}