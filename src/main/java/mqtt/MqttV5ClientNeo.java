package mqtt;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttTopic;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient.Mqtt5Publishes;
import com.hivemq.client.mqtt.mqtt5.datatypes.Mqtt5UserProperties;
import com.hivemq.client.mqtt.mqtt5.datatypes.Mqtt5UserPropertiesBuilder;
import com.hivemq.client.mqtt.mqtt5.datatypes.Mqtt5UserProperty;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PublishBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Stream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Collections;

import org.neo4j.logging.Log;
import org.neo4j.procedure.Context;

// --- MqttV5ClientNeo: 
public class MqttV5ClientNeo {

    public Mqtt5AsyncClient neo4jMqttClient;
    public Log log;

    public MqttV5ClientNeo(Map<String, Object> mqttConnectOptions, Log logIn) {
        log = logIn;

        // --- check - set mqtt connection dafaults
        if (!mqttConnectOptions.containsKey("serverHost")) {
            mqttConnectOptions.put("serverHost", "localhost");
        }

        if (mqttConnectOptions.containsKey("serverPort")) {
            int serverPort = Integer.parseInt(mqttConnectOptions.get("serverPort").toString());
            mqttConnectOptions.put("serverPort", (int) serverPort);
        } else {
            mqttConnectOptions.put("serverPort", 1883);
        }

        if (mqttConnectOptions.containsKey("neo4jMqttClientId")) {
            mqttConnectOptions.put("identifier", mqttConnectOptions.get("neo4jMqttClientId"));
        } else {
            mqttConnectOptions.put("identifier", "mqttNeo4j-" + UUID.randomUUID().toString());
        }
        
        // --- debug msg
        log.debug("mqtt - MqttV5ClientNeo: connect request - " + mqttConnectOptions);
        // --- setup client
        neo4jMqttClient = MqttClient.builder()
                .useMqttVersion5()
                .automaticReconnectWithDefaultConfig()
                .identifier(mqttConnectOptions.get("identifier").toString())
                .serverHost(mqttConnectOptions.get("serverHost").toString())
                .serverPort((int) mqttConnectOptions.get("serverPort"))
                .buildAsync();

        // --- connect
        String neo4jMqttClientId = neo4jMqttClient.getConfig().getClientIdentifier().toString();
        neo4jMqttClient.connect()
                .whenComplete((connAck, throwable) -> {
                    if (throwable != null) {
                        log.error("mqtt - MqttV5ClientNeo: connect ERROR - " + neo4jMqttClientId + " " + throwable);
                    } else {
                        log.info("mqtt - MqttV5ClientNeo: connect OK - " + neo4jMqttClientId + " " + connAck);
                    }
                });
    }

    public String getState() {
        return neo4jMqttClient.getState().toString();
    }

    public boolean isBrokerConnected() {
        String brokerconnectionState = neo4jMqttClient.getState().toString();
        if (brokerconnectionState == "CONNECTED") {
            return true;
        } else {
            return false;
        }
    }

    public void disconnect() {
        String neo4jMqttClientId = neo4jMqttClient.getConfig().getClientIdentifier().toString();
        neo4jMqttClient.disconnect();
        log.info("mqtt - MqttV5ClientNeo: disconnected - " + neo4jMqttClientId);
    }

    public boolean publish(String topic, String payload, Map<String, Object> userPropertiesMap,
            Map<String, Object> mqttPublishOptionsMap) {
        log.debug("mqtt - MqttV5ClientNeo: publish request - " + topic + " " + payload + " " + userPropertiesMap + " " + mqttPublishOptionsMap);
        // --- get broker id
        String neo4jMqttClientId = neo4jMqttClient.getConfig().getClientIdentifier().toString();
        // --- start building Mqtt5Publish object
        Mqtt5PublishBuilder.Complete publishBuilder = Mqtt5Publish.builder()
                .topic(topic)
                .payload(payload.getBytes());

        // --- create userProperties and add to userProperties
        Mqtt5UserPropertiesBuilder userPropertiesBuilder = Mqtt5UserProperties.builder();
        for (Map.Entry<String, Object> entry : userPropertiesMap.entrySet()) {
            userPropertiesBuilder.add(entry.getKey(), entry.getValue().toString());
        }
        Mqtt5UserProperties userProperties = userPropertiesBuilder.build();
        publishBuilder.userProperties(userProperties);

        if (mqttPublishOptionsMap.containsKey("responseTopic")) {
            publishBuilder.responseTopic((String) mqttPublishOptionsMap.get("responseTopic"));
        }
        if (mqttPublishOptionsMap.containsKey("correlationData")) {
            publishBuilder.correlationData(((String) mqttPublishOptionsMap.get("correlationData")).getBytes());
        }
        Mqtt5Publish publish = publishBuilder.build();
        // --- publish and get status
        boolean publisFailStatus = neo4jMqttClient.publish(publish)
                .whenComplete((publishResult, throwable) -> {
                    if (throwable != null) {
                        log.error("mqtt - MqttV5ClientNeo: publishMessage ERROR - " + neo4jMqttClientId + " " + throwable);
                    } else {
                        log.info("mqtt - MqttV5ClientNeo: publishMessage OK - " + neo4jMqttClientId + " " + publishResult);
                    }
                }).isCompletedExceptionally();
        // --- return status
        return !publisFailStatus;
    }

/*     public boolean publish(String topic, String payload, Map<String, Object> userPropertiesMap) {
        // --- get broker id
        String neo4jMqttClientId = neo4jMqttClient.getConfig().getClientIdentifier().toString();
        // --- start building Mqtt5Publish object
        Mqtt5PublishBuilder.Complete publishBuilder = Mqtt5Publish.builder()
                .topic(topic)
                .payload(payload.getBytes());

        // --- create userProperties and add to userProperties
        Mqtt5UserPropertiesBuilder userPropertiesBuilder = Mqtt5UserProperties.builder();
        for (Map.Entry<String, Object> entry : userPropertiesMap.entrySet()) {
            userPropertiesBuilder.add(entry.getKey(), entry.getValue().toString());
        }
        Mqtt5UserProperties userProperties = userPropertiesBuilder.build();
        publishBuilder.userProperties(userProperties);

        Mqtt5Publish publish = publishBuilder.build();
        // --- publish and get status
        boolean publisFailStatus = neo4jMqttClient.publish(publish)
                .whenComplete((publishResult, throwable) -> {
                    if (throwable != null) {
                        log.error("mqtt - MqttV5ClientNeo: publishMessage ERROR - " + neo4jMqttClientId + " " + throwable);
                    } else {
                        log.info("mqtt - MqttV5ClientNeo: publishMessage OK - " + neo4jMqttClientId + " " + publishResult);
                    }
                }).isCompletedExceptionally();
        // --- return status
        return !publisFailStatus;
    } */

    public boolean subscribe(String topic, ProcessMqttMessage task) { //
        String neo4jMqttClientId = neo4jMqttClient.getConfig().getClientIdentifier().toString();
        log.debug("mqtt - MqttV5ClientNeo: subscribe received - " + topic);
        // --- subscribe and get status
        boolean subscribeFailStatus = neo4jMqttClient.subscribeWith()
                .topicFilter(topic)
                .callback(message -> {
                    // --- return object is mqttMesageObject
                    Map<String, Object> mqttMesageObject = new HashMap<String, Object>();
                    // --- get payload
                    byte[] messagePayloadByte = message.getPayloadAsBytes();
                    String messagePayloadString = new String(messagePayloadByte);
                    mqttMesageObject.put("payload", messagePayloadString);
                    // --- get userProperties
                    Mqtt5UserProperties userProperties = message.getUserProperties();
                    List<Mqtt5UserProperty> userPropertyList = (List<Mqtt5UserProperty>) userProperties.asList();
                    Map<String, Object> userPropertyMap = new HashMap<String, Object>();
                    for (Mqtt5UserProperty userProperty : userPropertyList) {
                        String name = (String) userProperty.getName().toString();
                        String value = (String) userProperty.getValue().toString();
                        userPropertyMap.put(name, value);
                    }
                    mqttMesageObject.put("userProperties", userPropertyMap);
                    // --- ger response topic
                    Optional<MqttTopic> responseTopicMqtt = message.getResponseTopic();
                    responseTopicMqtt.ifPresent(string -> mqttMesageObject.put("responseTopic", string.toString()));
                    // --- get correlationData
                    Optional<ByteBuffer> correlationData = message.getCorrelationData();
                    Optional<String> correlationString = correlationData
                            .map(buffer -> StandardCharsets.UTF_8.decode(buffer).toString());
                    correlationString.ifPresent(string -> mqttMesageObject.put("correlationData", string));
                    // --- print
                    log.info("mqtt - MqttV5ClientNeo: mqtt message received - " + neo4jMqttClientId + " " + topic + " "+ mqttMesageObject.toString());
                    task.run(topic, mqttMesageObject);
                })
                .send()
                .whenComplete((subAck, throwable) -> {
                    if (throwable != null) {
                        log.info("mqtt - MqttV5ClientNeo: subscribe ERROR - " + neo4jMqttClientId + " " + topic + " "
                                + throwable);
                    } else {
                        log.info("mqtt - MqttV5ClientNeo: subscribe OK - " + neo4jMqttClientId + " " + topic + " " + subAck);
                    }
                }).isCompletedExceptionally();
        // --- return status
        return !subscribeFailStatus;
    }

    public void unsubscribe(String topic) {
        String neo4jMqttClientId = neo4jMqttClient.getConfig().getClientIdentifier().toString();
        neo4jMqttClient.unsubscribeWith()
                .topicFilter(topic)
                .send().whenComplete((connAck, throwable) -> {
                    log.info("mqtt - MqttV5ClientNeo: unsubscribed - " + neo4jMqttClientId + " " + topic + " " + connAck);
                });
    }
}
