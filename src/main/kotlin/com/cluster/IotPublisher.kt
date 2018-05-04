package com.cluster

import com.amazonaws.services.iot.AWSIot
import com.amazonaws.services.iot.AWSIotClient
import com.amazonaws.services.iot.client.AWSIotMqttClient
import com.amazonaws.services.iot.client.AWSIotQos
import com.amazonaws.services.iot.model.DescribeEndpointRequest
import java.util.*

class IotPublisher(
        val iotClient: AWSIot = AwsConfigurator.defaultClient(AWSIotClient.builder()),
        val accessKey: String = System.getenv("AWS_ACCESS_KEY_ID"),
        val secretKey: String = System.getenv("AWS_SECRET_ACCESS_KEY"),
        val sessionToken: String = System.getenv("AWS_SESSION_TOKEN")
){

    fun endpoint() = iotClient.describeEndpoint(DescribeEndpointRequest()).endpointAddress

    fun postEvent(event: String, topic: String = "loadInfo"){
        val client = AWSIotMqttClient(endpoint(), UUID.randomUUID().toString(), accessKey, secretKey, sessionToken)
        client.connect(2000)
        client.publish(topic, AWSIotQos.QOS0, event)
        client.disconnect()
    }
}