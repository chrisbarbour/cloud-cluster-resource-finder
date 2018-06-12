package com.cluster

import com.amazonaws.services.kms.AWSKMS
import com.amazonaws.services.kms.AWSKMSClient
import com.amazonaws.services.kms.model.EncryptRequest
import java.nio.ByteBuffer
import java.util.*
import com.amazonaws.services.kms.model.DecryptRequest

open class KmsClient(
        private val kmsClient: AWSKMS = AwsConfigurator.defaultClient(AWSKMSClient.builder()),
        private val encryptionKey: String? = System.getenv("ENCRYPTION_KEY")){

    fun encrypt(text: String): String{
        val data = text.toByteArray()
        val buffer = ByteBuffer.allocate(data.size)
        buffer.put(data)
        buffer.flip()
        val encryptRequest = EncryptRequest().withKeyId(encryptionKey).withPlaintext(buffer)
        val ciphertext = kmsClient.encrypt(encryptRequest).ciphertextBlob
        return getString(Base64.getEncoder().encode(ciphertext))
    }

    fun decrypt(cipherText: String): String{
        val decoded = Base64.getDecoder().decode(cipherText)
        val buffer = ByteBuffer.allocate(decoded.size)
        buffer.put(decoded)
        buffer.flip()
       return getString(kmsClient.decrypt(DecryptRequest().withCiphertextBlob(buffer)).plaintext)
    }

    private fun getString(buffer: ByteBuffer): String {
        val byteArray = ByteArray(buffer.remaining())
        buffer.get(byteArray)
        return String(byteArray)
    }
}