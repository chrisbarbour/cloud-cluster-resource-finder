package com.cluster

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.securitytoken.AWSSecurityTokenService
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest
import com.cluster.api.Account

open class AccessChecker(
        val stsClient: (credentials: AWSCredentialsProvider) -> AWSSecurityTokenService =  { AwsConfigurator.defaultClient(AWSSecurityTokenServiceClient.builder().withCredentials(it) ) }
){

    fun userHasAccess(accountId: String, credentials: Account.AwsAuth): Boolean{
        return true
    }
}
