package com.cluster.api

import com.cluster.data.DataFinder
import com.cluster.data.S3DataFinder
import org.http4k.core.*
import org.http4k.core.Status.Companion.OK
import org.http4k.format.Jackson
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import org.http4k.serverless.AppLoader

class UserHandler(private val dataFinder: DataFinder = S3DataFinder()): AppLoader {

    override fun invoke(environment: Map<String, String>) = routes(
            "/users/{username}" bind routes(
                    "/" bind Method.GET to authenticated(::user),
                    optionsRoute
            )
    )

    fun user(contexts: RequestContexts) = { request: Request ->
        val username = contexts[request].get<String>("username")!!
        val requestedUsername = usernameFrom(request)
        if(requestedUsername == username) {
            val user = dataFinder.userInfoFor(requestedUsername)
            val userAliasInfo = Account.UserAliasInfo(requestedUsername,
                user.accountAliases.map {
                    val realAccount = dataFinder.accountInfoFor(it.accountId)
                    Account.AliasInfo(it, realAccount.initialized, realAccount.loading, realAccount.accessLevelFor(requestedUsername))
                }
            )
            Response(OK).body(Jackson.asJsonString(userAliasInfo))
        }
        else Response(Status.FORBIDDEN)
                .body("The requested username must match the requesting username")
    }



    companion object {
        private fun usernameFrom(request: Request) = request.path("username")
    }
}