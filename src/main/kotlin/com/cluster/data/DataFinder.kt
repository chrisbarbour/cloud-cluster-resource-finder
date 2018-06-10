package com.cluster.data

import com.cluster.api.Account
interface DataFinder{
    fun userInfoFor(username: String): Account.User
    fun updateUserInfo(user: Account.User)
    fun accountInfoFor(accountId: String): Account
}
