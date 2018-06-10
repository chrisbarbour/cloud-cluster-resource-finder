package com.cluster.api

data class Account(val id: String, val initialized: Boolean = false, val loading: Boolean = false, val info: Meta? = null){
    data class Meta(val admins: List<String> = emptyList(), val viewers: List<String> = emptyList())
    enum class AccessLevel{ ADMIN, VIEW, PENDING, NONE }
    data class Alias(val account: Account, val name: String, val accessLevel: AccessLevel = AccessLevel.NONE)
    data class User(val username: String, val accountAliases: List<Alias> = emptyList())
}