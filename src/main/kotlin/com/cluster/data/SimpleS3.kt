package com.cluster.data

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.ObjectListing
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectResult
import java.io.InputStream

interface SimpleS3{
    fun doesObjectExist(bucket: String, key: String): Boolean
    fun getObjectAsString(bucket: String, key: String): String
    fun listObjects(bucket: String, key: String): ObjectListing
    fun putObject(bucket: String, key: String, body: InputStream, objectMetadata: ObjectMetadata): PutObjectResult
    fun putObject(bucket: String, key: String, body: String): PutObjectResult
}


class ActualS3(private val s3Client: AmazonS3): SimpleS3{
    override fun doesObjectExist(bucket: String, key: String) = s3Client.doesObjectExist(bucket, key)
    override fun getObjectAsString(bucket: String, key: String) = s3Client.getObjectAsString(bucket, key)
    override fun listObjects(bucket: String, key: String) = s3Client.listObjects(bucket, key)
    override fun putObject(bucket: String, key: String, body: InputStream, objectMetadata: ObjectMetadata) = s3Client.putObject(bucket, key, body, objectMetadata)
    override fun putObject(bucket: String, key: String, body: String) = s3Client.putObject(bucket, key, body)
}