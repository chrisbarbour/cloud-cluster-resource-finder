package com.cluster.api

import com.amazonaws.services.s3.model.ObjectListing
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectResult
import com.amazonaws.services.s3.model.S3ObjectSummary
import com.auth0.jwt.JWT
import com.cluster.data.S3DataFinder
import com.cluster.data.SimpleS3
import org.http4k.core.*
import org.http4k.filter.CorsPolicy
import org.http4k.filter.ServerFilters
import org.http4k.format.Jackson
import org.http4k.lens.Header
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.SunHttp
import org.http4k.server.asServer
import org.http4k.serverless.AppLoader
import java.io.InputStream


object ApiReactor: AppLoader{
    override fun invoke(environment: Map<String, String>) = routes(AccountHandler()(environment), UserHandler()(environment))
}

fun main(args: Array<String>){
    val dataFinder = S3DataFinder(LocalS3(), "")
    routes(
            AccountHandler(dataFinder ,"")(emptyMap()),
            UserHandler(dataFinder)(emptyMap())
    ).asServer(SunHttp(8080)).start()
}

val optionsRoute = "/" bind Method.OPTIONS to { Response(Status.OK).headers(listOf(
        "Access-Control-Allow-Origin" to "*",
        "Access-Control-Allow-Headers" to "*",
        "Access-Control-Allow-Methods" to "*"
)) }

private val tokenHeader = Header.map { it.substringAfter("Bearer ") }.required("Authorization")

fun authenticate(contexts: RequestContexts) = Filter { next ->
    { request ->
        contexts[request]["username"] = JWT.decode(tokenHeader.extract(request)).claims["cognito:username"]!!.asString()
        next(request)
    }
}
fun authenticated(method: (contexts: RequestContexts) -> HttpHandler) = with(RequestContexts()) {
    ServerFilters.InitialiseRequestContext(this)
            .then(ServerFilters.Cors(CorsPolicy.UnsafeGlobalPermissive))
            .then(authenticate(this))
            .then(method(this))
}

class LocalS3(private val localStorage: MutableMap<String, String> = mutableMapOf(
        "/users/localuser" to Jackson.asJsonString(Account.UserAliasInfo("localuser"))
)): SimpleS3 {
    override fun doesObjectExist(bucket: String, key: String) = localStorage.containsKey("$bucket/$key")

    override fun getObjectAsString(bucket: String, key: String) = localStorage["$bucket/$key"]!!

    override fun listObjects(bucket: String, key: String) = ObjectListing().also {
        it.bucketName = bucket
        it.objectSummaries.addAll(localStorage.filter { it.key.startsWith("$bucket/$key") }.map { entry ->
            S3ObjectSummary().also { summary ->
                summary.bucketName = bucket
                summary.key = entry.key.substringAfter("$bucket/")
            }
        })
    }

    override fun putObject(bucket: String, key: String, body: InputStream, objectMetadata: ObjectMetadata) = putObject(bucket, key, String(body.readBytes()))

    override fun putObject(bucket: String, key: String, body: String): PutObjectResult {
        localStorage["$bucket/$key"] = body
        return PutObjectResult()
    }

}
