package com.cluster.api

import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.AmazonSNSClient
import com.amazonaws.services.sns.model.PublishRequest
import com.cluster.AccessChecker
import com.cluster.AwsConfigurator
import com.cluster.KmsClient
import com.cluster.data.DataFinder
import com.cluster.data.S3DataFinder
import org.http4k.core.*
import org.http4k.core.Method.*
import org.http4k.core.Status.Companion.FORBIDDEN
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.format.Jackson
import org.http4k.format.Jackson.json
import org.http4k.format.Jackson.auto
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import org.http4k.serverless.AppLoader

class AccountHandler(
        private val dataFinder: DataFinder = S3DataFinder(),
        private val loadTopic: String = System.getenv("LOAD_TOPIC"),
        private val accessChecker: AccessChecker = AccessChecker(),
        private val kmsClient: KmsClient = KmsClient(),
        private val snsClient: AmazonSNS = AwsConfigurator.defaultClient(AmazonSNSClient.builder())
): AppLoader {

    override fun invoke(environment: Map<String, String>) = routes(
            "/accounts/{accountId}" bind routes(
                    "/alias/{alias}" bind routes(
                            "/" bind PUT to authenticated(::addAlias),
                            "/" bind DELETE to authenticated(::deleteAlias),
                            optionsRoute
                    ),
                    "/load" bind routes(
                            "/" bind Method.POST to  authenticated(::load),
                            optionsRoute
                    ),
                    "/collect" bind routes(
                            "/" bind GET to ::collect,
                            optionsRoute
                    ),
                   "/" bind GET to authenticated(::accountData),
                    optionsRoute
            )
    )

    private fun usernameFrom(contexts: RequestContexts, request: Request) = contexts[request].get<String>("username")!!

    private fun updateAlias(username: String, update: (aliases: List<Account.Alias>) -> List<Account.Alias>): Response {
        val userInfo = dataFinder.userInfoFor(username)
        val updatedUserInfo = userInfo.copy(accountAliases = update(userInfo.accountAliases))
        if(updatedUserInfo != userInfo) dataFinder.updateUserInfo(updatedUserInfo)
        return Response(Status.OK).with(
                Body.json().toLens() of Jackson.asJsonObject(updatedUserInfo)
        )
    }

    private fun aliasFrom(request: Request) = Account.Alias(accountIdFrom(request), aliasNameFrom(request))
    private fun aliasExists(aliases: List<Account.Alias>, accountId: String, alias: String) = aliases.find { it.accountId == accountId && it.name == alias } != null

    private fun addAlias(contexts: RequestContexts) = { request: Request -> updateAlias(usernameFrom(contexts, request)) { aliases ->
        if(!aliasExists(aliases, accountIdFrom(request), aliasNameFrom(request))) aliases + aliasFrom(request)
        else aliases
    } }
    private fun deleteAlias(contexts: RequestContexts) = { request: Request -> updateAlias(usernameFrom(contexts, request)) { it - aliasFrom(request) } }

    private fun collect(request: Request) = dataFinder.collect(accountIdFrom(request)).run { Response(OK) }

    private fun load(contexts: RequestContexts) = { request: Request ->
        val creds: Account.AwsAuth = awsAuthLens.extract(request)
        val accountId = accountIdFrom(request)
        val username = usernameFrom(contexts, request)
        if(hasAccess(accountId, creds)){
            resources.forEach {
                val loaderRequest = Account.ResourceLoaderRequest(accountId, it, username, creds, "us-east-1")
                val encryptedCreds = kmsClient.encrypt(Jackson.asJsonObject(loaderRequest).asText())
                snsClient.publish(PublishRequest().withTopicArn(loadTopic).withMessage(encryptedCreds))
            }
            Response(OK).body("User has Access, Loading resources: $resources")
        }
        else Response(FORBIDDEN)
    }

    private fun hasAccess(accountId: String, credentials: Account.AwsAuth): Boolean{
        return credentials.awsAccessKeyId.isNotEmpty() && accessChecker.userHasAccess(accountId, credentials)
    }

    fun accountData(contexts: RequestContexts) = { request: Request ->
        val accountId = accountIdFrom(request)
        val accountInfo = dataFinder.accountInfoFor(accountId)
        val username = contexts[request].get<String>("username")!!
        val accessLevel = accountInfo.accessLevelFor(username)
        if(accessLevel == Account.AccessLevel.VIEW || accessLevel == Account.AccessLevel.ADMIN){
            if(accountInfo.initialized && !accountInfo.loading){
                 Response(OK).body(dataFinder.accountTreeFor(accountId))
            }
            else Response(NOT_FOUND)
        }
        else Response(FORBIDDEN)
    }

    companion object {
        val resources = listOf("lambda", "iam", "sns", "s3", "api", "dynamo", "ec2", "ecs", "kms", "sqs")
        private val awsAuthLens = Body.auto<Account.AwsAuth>().toLens()
        private fun accountIdFrom(request: Request) = request.path("accountId")!!
        private fun aliasNameFrom(request: Request) = request.path("alias")!!
    }
}