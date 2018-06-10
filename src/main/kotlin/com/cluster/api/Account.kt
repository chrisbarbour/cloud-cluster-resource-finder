package com.cluster.api

data class Account(val id: String, val initialized: Boolean = false, val loading: Boolean = false, val info: Meta? = null){
    data class Meta(val admins: List<String> = emptyList(), val viewers: List<String> = emptyList(), val pendingRequests: List<String> = emptyList())
    enum class AccessLevel{ ADMIN, VIEW, PENDING, NONE }
    data class Alias(val accountId: String, val name: String)
    data class User(val username: String, val accountAliases: List<Alias> = emptyList())

    data class AliasInfo(val alias: Alias, val initialized: Boolean, val loading: Boolean, val accessLevel: AccessLevel = AccessLevel.NONE)
    data class UserAliasInfo(val username: String, val accountAliases: List<AliasInfo> = emptyList())

    fun accessLevelFor(username: String): AccessLevel{
        val meta = info ?: Meta()
        return when{
            meta.admins.contains(username) -> AccessLevel.ADMIN
            meta.viewers.contains(username) -> AccessLevel.VIEW
            meta.pendingRequests.contains(username) -> AccessLevel.PENDING
            else -> AccessLevel.NONE
        }
    }
}