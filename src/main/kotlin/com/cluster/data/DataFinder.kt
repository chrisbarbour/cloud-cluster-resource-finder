package com.cluster.data

import com.cluster.api.Account

interface DataFinder{
    fun userInfoFor(username: String): Account.User
    fun updateUserInfo(user: Account.User)
    fun accountInfoFor(accountId: String): Account
    fun updateAccountInfoFor(accountId: String, resource: String, username: String, info: Any)
    fun accountTreeFor(accountId: String): String
    fun collect(accountId: String, updateMeta: (meta: Account.Meta) -> Account.Meta = {it})
}
