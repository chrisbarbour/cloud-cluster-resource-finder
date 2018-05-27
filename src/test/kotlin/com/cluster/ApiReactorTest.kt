package com.cluster

import com.amazonaws.util.Base64
import com.auth0.jwt.JWT
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.Test

class ApiReactorTest{
    @Test
    fun go(){
       val token = JWT.decode("eyJraWQiOiJnU3c0QlwvcTFUZDJZMnZqck5SRmZqRzZrTWN0YTRlbDVxZUJcL2J0SFBtckE9IiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiJkZGQwMjdlYS03YTY5LTQxYzItOGFlMS01NDMxZjVjNzc2ZjgiLCJhdWQiOiI0Y3MxY2xtNXFuYm10YmpmaWQwaXB0aTRscSIsImVtYWlsX3ZlcmlmaWVkIjpmYWxzZSwiZXZlbnRfaWQiOiJjMTZkYzk3NS02MTkxLTExZTgtYmQ3ZC0wN2YwODcwMzBjMTQiLCJ0b2tlbl91c2UiOiJpZCIsImF1dGhfdGltZSI6MTUyNzQxMzkyNywiaXNzIjoiaHR0cHM6XC9cL2NvZ25pdG8taWRwLmV1LXdlc3QtMS5hbWF6b25hd3MuY29tXC9ldS13ZXN0LTFfNEtmT0ZyNW0zIiwiY29nbml0bzp1c2VybmFtZSI6ImJvYmphbWluIiwiZXhwIjoxNTI3NDIxNDM1LCJpYXQiOjE1Mjc0MTc4MzUsImVtYWlsIjoiYm9iamFtaW5AbGl2ZS5jby51ayJ9.Fc06PYSzrPXvmnxwg1mrEm4JBr46Aa3HuNv0EB9nlsQmdx0HgJZcWCKEQATMBHibeMVFYYCfqqKdj-SCwjVYK-9EVC0L8oPFHhcmCbvg8OaJoSp8i3BOdioRq_rUb4gnWz-hSJ-Fp19NyLJK4v7BSA1vQf-90lDNpz7LMvkDboQcxav3rK0FoRSI-HahKWTIxXD9IKn_DHh5tEsB0VBmdBGyVww883tnMGE0GlQCdotou0omhZPr7C3S1flU_J-vAH9Nfpt26fn38nu4UblfCsl1QGrSlvrUlwILxwtmiHQB89ReJ581392rLtFYgxv56MCUtrN1odwjnqLsoVN7dg")
        val payload = String(Base64.decode(token.payload))
        val username = jacksonObjectMapper().readValue<JsonNode>(payload)["cognito:username"].textValue()
        println(username)
    }
}