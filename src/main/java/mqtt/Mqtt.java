package mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
// import com.google.gson.Gson;
// import com.google.gson.JsonSyntaxException;
// import com.google.gson.reflect.TypeToken;

import java.util.stream.Stream;
// import java.nio.ByteBuffer;
// import java.nio.charset.StandardCharsets;
// import java.util.UUID;
// import java.util.Collections;
//import java.util.Random;

import org.neo4j.logging.Log;
import org.neo4j.procedure.Context;
import org.apache.commons.lang3.SerializationUtils;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.procedure.Mode;
import org.neo4j.procedure.Procedure;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.UserFunction;
// import org.neo4j.graphdb.RelationshipType;
// import org.neo4j.graphdb.ResourceIterator;

import apoc.result.MapResult;
import apoc.util.Util;

// import apoc.result.VirtualNode;
// import static apoc.util.Util.labelString;
// import apoc.result.VirtualRelationship;
// import apoc.result.VirtualPathResult;
// import apoc.result.GraphResult;
// import static apoc.cypher.CypherUtils.runCypherQuery;

// import mqtt.MqttV5ClientNeo;
// import mqtt.ProcessMqttMessage;
// import mqtt.AesCbcCriptDecript;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author bz
 * @since 20.11.2023
 *
 * 
 * 
 *
 */
public class Mqtt {

    public static final MapProcess mqttBrokersMap = new MapProcess();

    @Context
    public Log log;

    @Context
    public Transaction tx;

    @Context
    public GraphDatabaseService db;

    private static AesCbcCriptDecript aesCbcCriptDecript = new AesCbcCriptDecript();

    // ----------------------------------------------------------------------------------
    // generateAesCMCKeyIv
    // ----------------------------------------------------------------------------------
    @UserFunction
    @Description("RETURN mqtt.generateAesCbcKeyIv({})")
    public Map<String, Object> generateAesCbcKeyIv(
            @Name(value = "options", defaultValue = "{}") Map<String, Object> options) {
        log.debug("mqtt - mqtt: generateAesCbcKeyIv - request " + options.toString());
        Map<String, Object> keyIv = new HashMap<String, Object>();
        try {
            keyIv = aesCbcCriptDecript.generateAesKeyIv();
            log.debug("mqtt - mqtt: generateAesCbcKeyIv - OK: " + "keyIv hidden"); // keyIv.toString()
            return keyIv;
        } catch (Exception ex) {
            keyIv.put("ERROR", ex.toString());
            log.error("mqtt - mqtt: generateAesCbcKeyIv - ERROR: " + ex.toString());
            return keyIv;
        }
    }

    // ----------------------------------------------------------------------------------
    // add mqtt broker
    // ----------------------------------------------------------------------------------
    @UserFunction
    @Description("RETURN mqtt.connectBroker('neo4jMqttClientId', {serverHost:'localhost', serverPort:1883})")
    public Map<String, Object> connectBroker(
            @Name("neo4jMqttClientId") String neo4jMqttClientId,
            @Name("mqttConnectOptions") Map<String, Object> mqttConnectOptions) {
        log.debug("mqtt - mqtt: connectBroker - request " + neo4jMqttClientId + " " + mqttConnectOptions.toString());
        // --- check if already exists
        Map<String, Object> mqttBroker = mqttBrokersMap.getMapElementById(neo4jMqttClientId);
        if (!(mqttBroker == null)) {
            Map<String, Object> returnMessage = new HashMap<String, Object>();
            returnMessage.put("status", "ERROR");
            returnMessage.put("neo4jMqttClientId", neo4jMqttClientId);
            returnMessage.put("statusMessage", "Broker with this name already registred!");
            // --- log and return
            log.warn("mqtt - mqtt: connectBroker: " + returnMessage.toString());
            return returnMessage;
        } else {
            // --- set connections options
            mqttConnectOptions.put("neo4jMqttClientId", (String) neo4jMqttClientId);
            // --- create mqtt broker connection
            MqttV5ClientNeo mqttBrokerNeo4jClient = new MqttV5ClientNeo(mqttConnectOptions, log);
            // --- store broker info
            mqttConnectOptions.put("version", "Hive MQ MqTT V5 java client");
            mqttConnectOptions.put("connected", true);
            mqttConnectOptions.put("messagePublishLastStatus", "");
            mqttConnectOptions.put("messagePublishOk", 0);
            mqttConnectOptions.put("messagePublishError", 0);
            mqttConnectOptions.put("messageSubscribeOk", 0);
            mqttConnectOptions.put("messageSubscribeError", 0);
            mqttConnectOptions.put("subscribeList", new HashMap<String, Object>());
            // --- we will return map copy of mqttConnectOptions
            Map<String, Object> returnMessage = SerializationUtils.clone(new HashMap<>(mqttConnectOptions));
            // --- add mqtt connection to mqttBrokersMap
            mqttConnectOptions.put("mqttBrokerNeo4jClient", mqttBrokerNeo4jClient);
            mqttBrokersMap.addToMap(neo4jMqttClientId, mqttConnectOptions);
            // --- log and return
            log.info("mqtt - mqtt: connectBroker - connect OK " + neo4jMqttClientId + " " + mqttConnectOptions);
            return returnMessage;
        }
    }

    // ----------------------------------------------------------------------------------
    // list brokers
    // ----------------------------------------------------------------------------------
    @UserFunction
    @Description("RETURN mqtt.listBrokers()")
    public List<Map<String, Object>> listBrokers() {
        log.debug("mqtt - mqtt: listBrokers - request");
        List<Map<String, Object>> brokerList = mqttBrokersMap.getListFromMapAllClean();
        for (int i = 0; i < brokerList.size(); i++) {
            // --- get broker
            Map<String, Object> broker = brokerList.get(i);
            // --- return requested
            String neo4jMqttClientId = broker.get("neo4jMqttClientId").toString();
            // --- get mqtt connection
            Map<String, Object> mqttBroker = mqttBrokersMap.getMapElementById(neo4jMqttClientId);
            MqttV5ClientNeo mqttBrokerNeo4jClient = (MqttV5ClientNeo) mqttBroker.get("mqttBrokerNeo4jClient");
            // --- add connection status to map
            broker.put("status", mqttBrokerNeo4jClient.getState().toString());
        }
        log.debug("mqtt - mqtt: listBrokers: " + brokerList.toString());
        return brokerList;
    }

    // ----------------------------------------------------------------------------------
    // disconnect
    // ----------------------------------------------------------------------------------
    @UserFunction
    @Description("RETURN mqtt.disconnectBroker('neo4jMqttClientId',{})")
    public Map<String, Object> disconnectBroker(
            @Name("neo4jMqttClientId") String neo4jMqttClientId,
            @Name(value = "options", defaultValue = "{}") Map<String, Object> options) {
        log.debug("mqtt - mqtt: disconnectBroker - request " + neo4jMqttClientId + " " + options.toString());
        // --- get broker
        Map<String, Object> mqttBroker = mqttBrokersMap.getMapElementById(neo4jMqttClientId);
        // --- no broker found
        if (mqttBroker == null) {
            // --- return error
            Map<String, Object> returnMessage = new HashMap<String, Object>();
            returnMessage.put("status", "ERROR");
            returnMessage.put("neo4jMqttClientId", neo4jMqttClientId);
            returnMessage.put("statusMessage", "No broker with this name registred!");
            log.error("mqtt - mqtt: disconnectBroker ERROR " + returnMessage);
            return returnMessage;
        } else {
            // --- get broker connection
            MqttV5ClientNeo mqttBrokerNeo4jClient = (MqttV5ClientNeo) mqttBroker.get("mqttBrokerNeo4jClient");
            // --- disconnect and remove from map
            mqttBrokerNeo4jClient.disconnect();
            mqttBrokersMap.removeFromMap(neo4jMqttClientId);
            // --- return
            Map<String, Object> returnMessage = new HashMap<String, Object>();
            returnMessage.put("neo4jMqttClientId", neo4jMqttClientId);
            returnMessage.put("status", "OK");
            returnMessage.put("statusMessage", "MqTT Broker is disconnected and removed from list!");
            log.info("mqtt - mqtt: disconnectBroker OK " + returnMessage);
            return returnMessage;
        }
    }

    // ----------------------------------------------------------------------------------
    // publish
    // ----------------------------------------------------------------------------------
    @UserFunction
    @Description("RETURN mqtt.publishMessage('neo4jMqttClientId', 'mqtt/topic/path', {message:123}, {})")
    public Map<String, Object> publishMessage(
            @Name("neo4jMqttClientId") String neo4jMqttClientId,
            @Name("topic") String topic,
            @Name("message") Object message,
            @Name(value = "options", defaultValue = "{encription:'none'}") Map<String, Object> options) {
        log.debug("mqtt - mqtt: publishMessage - request " + neo4jMqttClientId + " " + topic + " " + message + " "
                + options.toString());
        // --- get broker
        Map<String, Object> mqttBroker = mqttBrokersMap.getMapElementById(neo4jMqttClientId);
        // --- no broker found
        if (mqttBroker == null) {
            // --- return error
            Map<String, Object> returnMessage = new HashMap<String, Object>();
            returnMessage.put("status", "ERROR");
            returnMessage.put("neo4jMqttClientId", neo4jMqttClientId);
            returnMessage.put("topic", topic);
            returnMessage.put("message", message);
            returnMessage.put("statusMessage", "No broker with this name registred!");
            log.error("mqtt - mqtt publish ERROR " + returnMessage);
            return returnMessage;
        } else {
            // --- get broker connection
            MqttV5ClientNeo mqttBrokerNeo4jClient = (MqttV5ClientNeo) mqttBroker.get("mqttBrokerNeo4jClient");
            // --- check status
            boolean mqttBrokerNeo4jClientStatus = mqttBrokerNeo4jClient.isBrokerConnected();
            if (mqttBrokerNeo4jClientStatus == false) {
                // --- set statistics
                mqttBroker.put("messagePublishLastStatus", "ERROR broker not connected");
                mqttBroker.put("messagePublishError", 1 + (int) mqttBroker.get("messagePublishError"));
                // --- return info
                Map<String, Object> returnMessage = new HashMap<String, Object>();
                returnMessage.put("status", "ERROR");
                returnMessage.put("neo4jMqttClientId", neo4jMqttClientId);
                returnMessage.put("topic", topic);
                returnMessage.put("message", message);
                returnMessage.put("connectionStatus", mqttBrokerNeo4jClient.getState().toString());
                returnMessage.put("statusMessage", "MqTT Publish ERROR - broker not connected");
                log.error("mqtt - mqtt publish ERROR " + returnMessage);
                return returnMessage;
            } else {
                Map<String, Object> dbResultObjectFinal = new HashMap<>();
                Map<String, Object> cypherQuerMetadata = new HashMap<>();
                cypherQuerMetadata.put("neo4jMqttClientId", neo4jMqttClientId);
                cypherQuerMetadata.put("requestMqttTopic", topic);
                cypherQuerMetadata.put("databaseName", db.databaseName());
                // --- check if list and send mqtt message
                try {
                    boolean publishStatus = false;
                    if (message instanceof List) {
                        log.debug("mqtt - mqtt: publish:  message instanceof List");
                        List<Map<String, Object>> data = new ArrayList<>();
                        dbResultObjectFinal.put("data", data);
                        List messageList = (List) message;
                        for (Object msg : messageList) {
                            publishStatus = checkObjectAndMqttPublish(mqttBrokerNeo4jClient, topic, message,
                                    cypherQuerMetadata, options);
                            log.debug(
                                    "mqtt - mqtt: publishMessage - message from list " + neo4jMqttClientId + " " + topic
                                            + " "
                                            + message + " " + options.toString());
                        }
                    }
                    // --- detect message object type and setup message
                    else {
                        publishStatus = checkObjectAndMqttPublish(mqttBrokerNeo4jClient, topic, message,
                                cypherQuerMetadata, options);
                        log.debug("mqtt - mqtt: publishMessage - message : " + neo4jMqttClientId + " " + topic + " "
                                + message + " "
                                + options.toString());
                    }
                    // --- check status and craft response message
                    if (publishStatus == true) {
                        // --- set statistics
                        mqttBroker.put("messagePublishLastStatus", "OK");
                        mqttBroker.put("messagePublishOk", 1 + (int) mqttBroker.get("messagePublishOk"));
                        // --- return info
                        Map<String, Object> returnMessage = new HashMap<String, Object>();
                        returnMessage.put("status", "OK");
                        returnMessage.put("neo4jMqttClientId", neo4jMqttClientId);
                        returnMessage.put("topic", topic);
                        returnMessage.put("message", message);
                        returnMessage.put("statusMessage", "MqTT Publish OK");
                        log.info("mqtt - mqtt: publish: publish OK " + returnMessage.toString());
                        return returnMessage;
                    } else {
                        // --- set statistics
                        mqttBroker.put("messagePublishLastStatus", "ERROR");
                        mqttBroker.put("messagePublishError", 1 + (int) mqttBroker.get("messagePublishError"));
                        // --- return info
                        Map<String, Object> returnMessage = new HashMap<String, Object>();
                        returnMessage.put("status", "ERROR");
                        returnMessage.put("neo4jMqttClientId", neo4jMqttClientId);
                        returnMessage.put("topic", topic);
                        returnMessage.put("message", message);
                        returnMessage.put("statusMessage", "MqTT Publish ERROR: " + " Hive MQ publish ERROR ");
                        log.error("mqtt - mqtt: publish Hive MQ ERROR " + returnMessage.toString());
                        return returnMessage;
                    }
                } catch (Exception ex) {
                    // --- set statistics
                    mqttBroker.put("messagePublishLastStatus", "ERROR: " + ex.getMessage());
                    mqttBroker.put("messagePublishError", 1 + (int) mqttBroker.get("messagePublishError"));
                    // --- return info
                    Map<String, Object> returnMessage = new HashMap<String, Object>();
                    returnMessage.put("status", "ERROR");
                    returnMessage.put("neo4jMqttClientId", neo4jMqttClientId);
                    returnMessage.put("topic", topic);
                    returnMessage.put("message", message);
                    returnMessage.put("statusMessage", "MqTT Publish ERROR: " + ex.getMessage());
                    log.error("mqtt - mqtt: publish Hive MQ ERROR " + returnMessage);
                    return returnMessage;
                }
            }
        }
    }

    // ----------------------------------------------------------------------------------
    // publishGrph
    // ----------------------------------------------------------------------------------
    @Procedure(mode = Mode.WRITE)
    @Description("CALL mqtt.publishGrph('neo4jMqttClientId', 'mqtt/topic/path','MERGE (n:MqttTest) ON CREATE SET n.count=1, n.message=$message ON MATCH SET n.count = n.count +1, n.message=$message RETURN n', {message:123})")
    public Stream<MapResult> publishGrph(
            @Name("neo4jMqttClientId") String neo4jMqttClientId,
            @Name("topic") String topic,
            @Name("query") String query,
            @Name(value = "params", defaultValue = "{}") Map<String, Object> params,
            @Name(value = "options", defaultValue = "{encription:'none'}") Map<String, Object> options) {
        log.debug("mqtt - mqtt: publishGrph - request " + neo4jMqttClientId + " " + topic + " " + " " + options);
        // --- get broker
        Map<String, Object> mqttBroker = mqttBrokersMap.getMapElementById(neo4jMqttClientId);
        // --- check if exists
        if (mqttBroker == null) {
            Map<String, Object> returnMessage = new HashMap<String, Object>();
            returnMessage.put("status", "ERROR");
            returnMessage.put("neo4jMqttClientId", neo4jMqttClientId);
            returnMessage.put("topic", topic);
            returnMessage.put("options", options);
            returnMessage.put("statusMessage", "Failed to find MqTT Broker - Check Connection");
            log.error("mqtt - mqtt: publishGrph: " + returnMessage);
            return Stream.of(returnMessage).map(MapResult::new);
        } else {
            // --- get broker connection
            MqttV5ClientNeo mqttBrokerNeo4jClient = (MqttV5ClientNeo) mqttBroker.get("mqttBrokerNeo4jClient");
            // --- check status if connected
            boolean mqttBrokerNeo4jClientStatus = mqttBrokerNeo4jClient.isBrokerConnected();
            if (mqttBrokerNeo4jClientStatus == false) {
                // --- statistics
                mqttBroker.put("messageSubscribeError", 1 + (int) mqttBroker.get("messageSubscribeError"));
                // --- return info
                Map<String, Object> returnMessage = new HashMap<String, Object>();
                returnMessage.put("status", "ERROR");
                returnMessage.put("neo4jMqttClientId", neo4jMqttClientId);
                returnMessage.put("topic", topic);
                returnMessage.put("statusMessage", "MqTT Subscribe Error: " + "NOT Connected");
                log.error("mqtt - mqtt: publishGrph: " + returnMessage);
                return Stream.of(returnMessage).map(MapResult::new);
            } else {
                // --- setup response objects - graph + cypherQuerMetadata
                List<Map<String, Object>> nodes = new ArrayList<>();
                List<Map<String, Object>> relationships = new ArrayList<>();
                Map<String, Object> dbResultObjectFinal = new HashMap<>();
                dbResultObjectFinal.put("nodes", nodes);
                dbResultObjectFinal.put("relationships", relationships);
                // --- set metadata
                Map<String, Object> cypherQuerMetadata = new HashMap<>();
                cypherQuerMetadata.put("neo4jMqttClientId", neo4jMqttClientId);
                cypherQuerMetadata.put("publishTopic", topic);
                cypherQuerMetadata.put("databaseName", db.databaseName());
                cypherQuerMetadata.put("cypherQuery", query);
                cypherQuerMetadata.put("cypherParams", params.toString());
                cypherQuerMetadata.put("nodes", 0);
                cypherQuerMetadata.put("relationships", 0);
                // --- do transaction
                try (Transaction tx = db.beginTx()) {
                    log.debug("mqtt -  mqtt: Transaction beginTx: ");
                    // ---
                    try (Result dbResult = tx.execute(query, params);) {
                        log.debug("mqtt -  mqtt: Transaction try: ");
                        while (dbResult.hasNext()) {
                            log.debug("mqtt -  mqtt: Transaction hasNext: ");
                            Map<String, Object> row = dbResult.next();
                            log.debug("mqtt -  mqtt: Transaction hasNext: ");
                            for (String key : dbResult.columns()) {
                                if (row.get(key) instanceof Node) {
                                    Node node = (Node) row.get(key);
                                    Map<String, Object> dbResultObject = nodeToMap(db, node);
                                    nodes.add(dbResultObject);
                                    cypherQuerMetadata.put("nodes", (int) cypherQuerMetadata.get("nodes") + 1);
                                } else if (row.get(key) instanceof Relationship) {
                                    Relationship link = (Relationship) row.get(key);
                                    Map<String, Object> dbResultObject = relationToMap(db, (Relationship) link);
                                    relationships.add(dbResultObject);
                                    cypherQuerMetadata.put("relationships",
                                            (int) cypherQuerMetadata.get("relationships") + 1);
                                } else {
                                    // --- we do not expect other elements - only graph
                                    cypherQuerMetadata.put("nonGraphDetected", "true");
                                    cypherQuerMetadata.put("nonGraph", (String) cypherQuerMetadata.get("nonGraph") + ","
                                            + key + ":" + row.get(key).toString());
                                }
                            }
                        }
                    }
                    tx.commit();
                    // --- set status
                    cypherQuerMetadata.put("cypherQueryStatus", "OK");
                    // --- set statistics
                    mqttBroker.put("messagePublishLastStatus", "OK");
                    mqttBroker.put("messagePublishOk", 1 + (int) mqttBroker.get("messagePublishOk"));
                    // --- log
                    String lastMessageProcessedResults = "mqtt - ProcessMqttMessage "
                            + "\n neo4jMqttClientId: " + neo4jMqttClientId
                            + "\n toppicRequest: " + topic
                            + "\n cypherQuery: \n " + query
                            + "\n cypherQuery result:\n " + dbResultObjectFinal.toString()
                            + "\n";
                    log.debug("mqtt - mqtt:" + lastMessageProcessedResults);

                } catch (Exception ex) {
                    // --- setup dbResultObjectFinal object
                    dbResultObjectFinal.put("error", ex.toString());
                    // --- setup cypherQuerMetadata object
                    cypherQuerMetadata.put("cypherQueryStatus", "error");
                    // --- set statistics
                    mqttBroker.put("messagePublishLastStatus", "ERROR: " + ex.getMessage());
                    mqttBroker.put("messagePublishError", 1 + (int) mqttBroker.get("messagePublishError"));
                }

                // --- publish message
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    String dbResultString;
                    dbResultString = mapper.writeValueAsString(dbResultObjectFinal).toString();
                    mqttBrokerNeo4jClient.publish(topic, dbResultString, cypherQuerMetadata, options);
                    return Stream.of(dbResultObjectFinal).map(MapResult::new);
                } catch (JsonProcessingException ex) {
                    // --- statistics
                    mqttBroker.put("messageSubscribeError", 1 + (int) mqttBroker.get("messageSubscribeError"));
                    // --- return info
                    Map<String, Object> returnMessage = new HashMap<String, Object>();
                    returnMessage.put("status", "ERROR");
                    returnMessage.put("neo4jMqttClientId", neo4jMqttClientId);
                    returnMessage.put("topic", topic);
                    returnMessage.put("statusMessage", "MqTT Publish Error: " + ex.toString());
                    log.error("mqtt - mqtt: publishGrph: " + returnMessage);
                    return Stream.of(returnMessage).map(MapResult::new);
                }
            }
        }
    }

    // ----------------------------------------------------------------------------------
    // subscribe
    // ----------------------------------------------------------------------------------
    @Procedure(mode = Mode.WRITE)
    @Description("CALL mqtt.subscribeCypherRun('neo4jMqttClientId', 'mqtt/topic/cypherRunRequest','MERGE (n:MqttTest) ON CREATE SET n.count=1, n.message=$message ON MATCH SET n.count = n.count +1, n.message=$message RETURN n', {responseTopic:'mqtt/topic/cypherRunDefaultResponse'}) ")
    public Stream<MapResult> subscribeCypherRun(
            @Name("neo4jMqttClientId") String neo4jMqttClientId,
            @Name("topic") String topic,
            @Name("query") String query,
            @Name(value = "options", defaultValue = "{responseTopic:'', encription:'none'}") Map<String, Object> options) {
        log.debug("mqtt - mqtt: subscribeCypherRun - request " + neo4jMqttClientId + " " + topic + " " + query + " "
                + options);
        // --- remove subscription if exist
        this.unSubscribe(neo4jMqttClientId, topic);
        // --- get broker
        Map<String, Object> mqttBroker = mqttBrokersMap.getMapElementById(neo4jMqttClientId);
        if (mqttBroker == null) {
            Map<String, Object> returnMessage = new HashMap<String, Object>();
            returnMessage.put("status", "ERROR");
            returnMessage.put("neo4jMqttClientId", neo4jMqttClientId);
            returnMessage.put("topic", topic);
            returnMessage.put("query", query);
            returnMessage.put("options", options);
            returnMessage.put("statusMessage", "Failed to find MqTT Broker - Check Connection");
            log.error("mqtt - mqtt: subscribe: " + returnMessage);
            return Stream.of(returnMessage).map(MapResult::new);
        } else {
            // --- get broker connection
            MqttV5ClientNeo mqttBrokerNeo4jClient = (MqttV5ClientNeo) mqttBroker.get("mqttBrokerNeo4jClient");
            // --- check status
            boolean mqttBrokerNeo4jClientStatus = mqttBrokerNeo4jClient.isBrokerConnected();
            if (mqttBrokerNeo4jClientStatus == false) {
                // --- statistics
                mqttBroker.put("messageSubscribeError", 1 + (int) mqttBroker.get("messageSubscribeError"));
                // --- return info
                Map<String, Object> returnMessage = new HashMap<String, Object>();
                returnMessage.put("status", "ERROR");
                returnMessage.put("neo4jMqttClientId", neo4jMqttClientId);
                returnMessage.put("topic", topic);
                returnMessage.put("statusMessage", "MqTT Subscribe Error: " + "NOT Connected");

                log.error("mqtt - mqtt: subscribe: " + returnMessage);
                return Stream.of(returnMessage).map(MapResult::new);
            } else {

                // --- set options
                Map<String, Object> subscribeOptions = new HashMap<String, Object>();
                subscribeOptions.put("neo4jMqttClientId", neo4jMqttClientId);
                subscribeOptions.put("topic", topic);
                subscribeOptions.put("subsctiption", "subscribeCypherRun");
                subscribeOptions.put("query", query);
                subscribeOptions.put("options", options);
                subscribeOptions.put("lastMessageReceived", "subscribed");
                subscribeOptions.put("messageReceivedOk", 0);
                subscribeOptions.put("messageReceivedError", 0);
                subscribeOptions.put("responseTopicDefault", getValueOrDefault(options.get("responseTopic"), ""));

                // --- create cypher task
                ProcessMqttMessage task = new ProcessMqttMessage(db, mqttBrokerNeo4jClient, subscribeOptions, log);
                // --- subscribe to mqtt

                boolean subscribeStatus = mqttBrokerNeo4jClient.subscribe(topic, task);

                if (subscribeStatus == true) {
                    log.info("mqtt - mqtt: subscribe OK: ");

                    // --- add to subscription list
                    Map<String, Object> subscribeList = (Map<String, Object>) mqttBroker.get("subscribeList");
                    subscribeList.put(topic, subscribeOptions);
                    // --- statistics
                    mqttBroker.put("messageSubscribeOk", 1 + (int) mqttBroker.get("messageSubscribeOk"));
                    // --- return
                    Map<String, Object> returnMessage = new HashMap<String, Object>();
                    returnMessage.put("status", "ok");
                    returnMessage.put("neo4jMqttClientId", neo4jMqttClientId);
                    returnMessage.put("topic", topic);
                    returnMessage.put("query", query);
                    returnMessage.put("options", options);
                    returnMessage.put("statusMessage", "MqTT Subscribe ok");
                    log.info("mqtt - mqtt: subscribe " + returnMessage);
                    return Stream.of(returnMessage).map(MapResult::new);

                } else {
                    mqttBroker.put("messageSubscribeError", 1 + (int) mqttBroker.get("messageSubscribeError"));
                    // --- return info
                    Map<String, Object> returnMessage = new HashMap<String, Object>();
                    returnMessage.put("status", "ERROR");
                    returnMessage.put("neo4jMqttClientId", neo4jMqttClientId);
                    returnMessage.put("topic", topic);
                    returnMessage.put("statusMessage", "MqTT Subscribe Error: ");

                    log.error("mqtt - mqtt: subscribe " + returnMessage);
                    return Stream.of(returnMessage).map(MapResult::new);
                }
            }
        }
    }

    // ----------------------------------------------------------------------------------
    // subscribeCypherQuery
    // ----------------------------------------------------------------------------------
    @Procedure(mode = Mode.WRITE)
    @Description("CALL mqtt.subscribeCypherQuery('neo4jMqttClientId', 'neo4j/cypheQuery/reqest', {responseTopic:'neo4j/cypherQuery/resultDefault'}) ")
    public Stream<MapResult> subscribeCypherQuery(
            @Name("neo4jMqttClientId") String neo4jMqttClientId,
            @Name("topic") String topic,
            @Name(value = "options", defaultValue = "{responseTopic:'', encription:'none'}") Map<String, Object> options) {
        log.debug("mqtt - mqtt: subscribe - request " + neo4jMqttClientId + " " + topic + " " + " " + options);
        // --- remove subscription if exist
        this.unSubscribe(neo4jMqttClientId, topic);
        // --- get broker
        Map<String, Object> mqttBroker = mqttBrokersMap.getMapElementById(neo4jMqttClientId);
        if (mqttBroker == null) {
            Map<String, Object> returnMessage = new HashMap<String, Object>();
            returnMessage.put("status", "ERROR");
            returnMessage.put("neo4jMqttClientId", neo4jMqttClientId);
            returnMessage.put("topic", topic);
            returnMessage.put("options", options);
            returnMessage.put("statusMessage", "Failed to find MqTT Broker - Check Connection");
            log.error("mqtt - mqtt: subscribe " + returnMessage);
            return Stream.of(returnMessage).map(MapResult::new);
        } else {
            // --- get broker connection
            MqttV5ClientNeo mqttBrokerNeo4jClient = (MqttV5ClientNeo) mqttBroker.get("mqttBrokerNeo4jClient");
            // --- check status
            boolean mqttBrokerNeo4jClientStatus = mqttBrokerNeo4jClient.isBrokerConnected();
            if (mqttBrokerNeo4jClientStatus == false) {
                // --- statistics
                mqttBroker.put("messageSubscribeError", 1 + (int) mqttBroker.get("messageSubscribeError"));
                // --- return info
                Map<String, Object> returnMessage = new HashMap<String, Object>();
                returnMessage.put("status", "ERROR");
                returnMessage.put("neo4jMqttClientId", neo4jMqttClientId);
                returnMessage.put("topic", topic);
                returnMessage.put("statusMessage", "MqTT Subscribe Error: " + "NOT Connected");

                log.error("mqtt - mqtt: subscribe " + returnMessage);
                return Stream.of(returnMessage).map(MapResult::new);
            } else {
                // --- set options
                Map<String, Object> subscribeOptions = new HashMap<String, Object>();

                subscribeOptions.put("neo4jMqttClientId", neo4jMqttClientId);
                subscribeOptions.put("topic", topic);
                subscribeOptions.put("responseTopicDefault",
                        getValueOrDefault(options.get("responseTopic"), ""));
                subscribeOptions.put("subsctiption", "subscribeCypherQuery");
                subscribeOptions.put("lastMessageReceived", "subscribed");
                subscribeOptions.put("messageReceivedOk", 0);
                subscribeOptions.put("messageReceivedError", 0);
                subscribeOptions.put("options", options);

                // --- create cypher task
                ProcessMqttMessage task = new ProcessMqttMessage(db, mqttBrokerNeo4jClient, subscribeOptions, log);
                // --- subscribe to mqtt

                boolean subscribeStatus = mqttBrokerNeo4jClient.subscribe(topic, task);

                if (subscribeStatus == true) {
                    log.info("mqtt - mqtt: subscribe OK ");

                    // --- add to subscription list
                    Map<String, Object> subscribeList = (Map<String, Object>) mqttBroker.get("subscribeList");
                    subscribeList.put(topic, subscribeOptions);
                    // --- statistics
                    mqttBroker.put("messageSubscribeOk", 1 + (int) mqttBroker.get("messageSubscribeOk"));
                    // --- return
                    Map<String, Object> returnMessage = new HashMap<String, Object>();
                    returnMessage.put("status", "ok");
                    returnMessage.put("neo4jMqttClientId", neo4jMqttClientId);
                    returnMessage.put("topic", topic);
                    returnMessage.put("options", options);
                    returnMessage.put("statusMessage", "MqTT Subscribe ok");
                    log.info("mqtt - mqtt: subscribe: " + returnMessage);
                    return Stream.of(returnMessage).map(MapResult::new);

                } else {
                    mqttBroker.put("messageSubscribeError", 1 + (int) mqttBroker.get("messageSubscribeError"));
                    // --- return info
                    Map<String, Object> returnMessage = new HashMap<String, Object>();
                    returnMessage.put("status", "ERROR");
                    returnMessage.put("neo4jMqttClientId", neo4jMqttClientId);
                    returnMessage.put("topic", topic);
                    returnMessage.put("statusMessage", "MqTT Subscribe Error: ");

                    log.error("mqtt - mqtt: subscribe: " + returnMessage);
                    return Stream.of(returnMessage).map(MapResult::new);
                }

            }

        }
    }

    // ----------------------------------------------------------------------------------

    // ----------------------------------------------------------------------------------
    // unsubscribe
    // ----------------------------------------------------------------------------------
    @UserFunction
    @Description("RETURN mqtt.unSubscribe('neo4jMqttClientId', 'mqtt/topic/path' )")
    public Object unSubscribe(
            @Name("neo4jMqttClientId") String neo4jMqttClientId,
            @Name("topic") String topic) {
        log.debug("mqtt - mqtt: unSubscribe: " + neo4jMqttClientId + " " + topic);
        // --- get broker
        Map<String, Object> mqttBroker = mqttBrokersMap.getMapElementById(neo4jMqttClientId);
        if (mqttBroker != null) {
            MqttV5ClientNeo mqttBrokerNeo4jClient = (MqttV5ClientNeo) mqttBroker.get("mqttBrokerNeo4jClient");
            // --- unsubscribe
            mqttBrokerNeo4jClient.unsubscribe(topic);
            Map<String, Object> subscribeList = (Map<String, Object>) mqttBroker.get("subscribeList");
            // --- remove from list
            subscribeList.remove(topic);
            // --- statistics
            mqttBroker.put("messageSubscribeOk", (int) mqttBroker.get("messageSubscribeOk") - 1);
            // --- return
            Map<String, Object> returnMessage = new HashMap<String, Object>();
            returnMessage.put("status", "ok");
            returnMessage.put("neo4jMqttClientId", neo4jMqttClientId);
            returnMessage.put("broker topic", topic);
            returnMessage.put("statusMessage", "UnSubscribe ok");
            log.info("mqtt - mqtt: unSubscribe: " + returnMessage);
            return returnMessage;
        } else {
            // --- return
            Map<String, Object> returnMessage = new HashMap<String, Object>();
            returnMessage.put("status", "ok");
            returnMessage.put("neo4jMqttClientId", neo4jMqttClientId);
            returnMessage.put("broker topic", topic);
            returnMessage.put("statusMessage", "No broker to unSubscribe");
            log.info("mqtt - mqtt: unSubscribe: " + returnMessage);
            return returnMessage;
        }
    }

    // ----------------------------------------------------------------------------------
    // list subscriptions
    // ----------------------------------------------------------------------------------
    @UserFunction
    @Description("RETURN mqtt.listSubscriptions('optional neo4jMqttClientId default to all')")
    public List<Map<String, Object>> listSubscriptions(
            @Name(value = "neo4jMqttClientId", defaultValue = "all") String neo4jMqttClientId) {
        log.debug("mqtt - mqtt: listSubscriptions: " + neo4jMqttClientId);
        // --- subscriptions list
        List<Map<String, Object>> subscribeList = new ArrayList<Map<String, Object>>();
        // --- loop brokers
        for (int i = 0; i < mqttBrokersMap.getListFromMapAllClean().size(); i++) {
            // --- get broker
            Map<String, Object> broker = mqttBrokersMap.getListFromMapAllClean().get(i);
            // --- return requested
            if ((neo4jMqttClientId.equals("all")) || (broker.get("name").equals(neo4jMqttClientId))) {
                // --- get subscriptions
                Map<String, Object> brokerSubscriptions = (Map<String, Object>) broker.get("subscribeList");
                for (Map.Entry<String, Object> entry : brokerSubscriptions.entrySet()) {
                    String topic = entry.getKey();
                    Object subscribeOptions = entry.getValue();

                    Map<String, Object> subscriptionMap = new HashMap<String, Object>();
                    subscriptionMap.put("neo4jMqttClientId", broker.get("neo4jMqttClientId") + "-" + topic);
                    subscriptionMap.put("type", "MqttSubscription");
                    subscriptionMap.put("neo4jMqttClientId", broker.get("neo4jMqttClientId"));
                    subscriptionMap.put("topic", topic);
                    subscriptionMap.put("subscribeOptions", subscribeOptions);
                    subscribeList.add(subscriptionMap);
                }
            } else {
                log.debug("mqtt - mqtt: listSubscriptions ignoring  " + neo4jMqttClientId + " " + broker.get("name"));
            }
        }
        log.debug("mqtt - mqtt: listSubscriptions for " + neo4jMqttClientId + " " + subscribeList.toString());
        return subscribeList;
    }

    // ----------------------------------------------------------------------------------
    // utils
    // ----------------------------------------------------------------------------------

    public static Map<String, Object> nodeToMap(GraphDatabaseService db, Node message) {
        // Map<String, Object> dbResultObject = nodeToMap( db, message)
        // Map<String, Object> dbResultObject = relationToMap( db, message)
        Node node = (Node) message;
        Map<String, Object> dbResultObject = new HashMap<>();
        dbResultObject.put("identity", (int) ((Node) node).getId());
        dbResultObject.put("elementId", (String) ((Node) node).getElementId());
        dbResultObject.put("labels", (List<String>) Util.labelStrings((Node) node)); // (String)((Node)
        dbResultObject.put("properties", (Map<String, Object>) ((Node) node).getAllProperties());
        dbResultObject.put("database", (String) db.databaseName());
        return dbResultObject;
    }

    public static Map<String, Object> relationToMap(GraphDatabaseService db, Relationship message) {
        // Map<String, Object> dbResultObject = relationToMap( db, message)
        Relationship link = (Relationship) message;
        Map<String, Object> dbResultObject = new HashMap<>();
        dbResultObject.put("identity", (int) ((Relationship) link).getId());
        dbResultObject.put("end", (int) ((Relationship) link).getEndNodeId());
        dbResultObject.put("strt", (int) ((Relationship) link).getStartNodeId());

        dbResultObject.put("elementId", (String) ((Relationship) link).getElementId());
        dbResultObject.put("satartElementId", (String) ((Relationship) link).getStartNode().getElementId());
        dbResultObject.put("endElementId", (String) ((Relationship) link).getEndNode().getElementId());

        dbResultObject.put("type", (String) ((Relationship) link).getType().toString());
        dbResultObject.put("properties", (Map<String, Object>) ((Relationship) link).getAllProperties());
        dbResultObject.put("database", (String) db.databaseName());
        return dbResultObject;
    }

    // --- MapProcess
    public final static class MapProcess {

        public final Map<String, Object> map;
        MapProcess.CleanObject cleanObject = new MapProcess.CleanObject();

        public MapProcess() {
            map = new HashMap();
        }

        public Map<String, Object> getMapAll() {
            return map;
        }

        public Map<String, Object> addToMap(String name, Map<String, Object> mapTmp) {
            map.put(name, mapTmp);
            return mapTmp;
        }

        public void removeFromMap(String name) {
            map.remove(name);
        }

        public Map<String, Object> getMapElementById(String id) {
            Map<String, Object> mapTmp = (Map) map.get(id);
            return mapTmp;
        }

        public Map<String, Object> getMapElementByIdClean(String id) {
            Map<String, Object> mapTmp = (Map) map.get(id);
            return cleanObject.cleanMap(mapTmp);
        }

        public ArrayList<Map<String, Object>> getListFromMapAll() {
            List<String> mapKeys = new ArrayList(map.keySet());
            ArrayList<Map<String, Object>> listMap = new ArrayList();

            for (int i = 0; i < mapKeys.size(); i++) {
                Map<String, Object> mapTmp = (Map) map.get(mapKeys.get(i));

                listMap.add(mapTmp);
            }
            return listMap;
        }

        public ArrayList<Map<String, Object>> getListFromMapAllClean() {
            List<String> mapKeys = new ArrayList(map.keySet());
            ArrayList<Map<String, Object>> listMap = new ArrayList();

            for (int i = 0; i < mapKeys.size(); i++) {
                Map<String, Object> mapTmp = (Map) map.get(mapKeys.get(i));

                listMap.add(cleanObject.cleanMap(mapTmp));
            }
            return listMap;
        }

        public ArrayList<Map<String, Object>> getListFromMap() {
            List<String> mapKeys = new ArrayList(map.keySet());
            ArrayList<Map<String, Object>> listMap = new ArrayList();
            for (int i = 0; i < mapKeys.size(); i++) {
                Map<String, Object> mapTmp = (Map) map.get(mapKeys.get(i));

                listMap.add(mapTmp);
            }
            return listMap;
        }

        public class CleanObject {

            public CleanObject() {

            }

            public Map<String, Object> cleanMap(final Map<String, Object> mapInput) {
                final Map<String, Object> mapTmp = new HashMap<String, Object>();
                final List<String> mapKeys = new ArrayList<String>(mapInput.keySet());
                for (int i = 0; i < mapKeys.size(); ++i) {
                    final Object mapObject = mapInput.get(mapKeys.get(i));
                    if (mapObject instanceof String
                            || mapObject instanceof Integer
                            || mapObject instanceof Boolean
                            || mapObject instanceof Float
                            || mapObject instanceof Double
                            || mapObject instanceof Map) {
                        mapTmp.put(mapKeys.get(i), mapObject);
                    }
                }
                return mapTmp;
            }

            // public ArrayList<Node> cleanNodeList(final Map<String, Object> mapInput,
            // List<String> labelNames, GraphDatabaseService db) {
            // final Map<String, Object> mapTmp = new HashMap<String, Object>();
            // final List<String> mapKeys = new ArrayList<String>(mapInput.keySet());
            // final ArrayList<Node> nodes = new ArrayList();
            // for (int i = 0; i < mapKeys.size(); ++i) {
            // final Object mapObject = mapInput.get(mapKeys.get(i));
            // if (mapObject instanceof String
            // || mapObject instanceof Integer
            // || mapObject instanceof Boolean
            // || mapObject instanceof Float
            // || mapObject instanceof Double
            // || mapObject instanceof Map) {
            // mapTmp.put(mapKeys.get(i), mapObject);

            // }
            // nodes.add(new VirtualNode(Util.labels(labelNames), mapTmp, db));
            // }
            // return nodes;
            // }
        }
    }

    public static <T> T getValueOrDefault(T value, T defaultValue) {
        /*
         * //usee map:
         * Map<String, Object> cypherOptions = getValueOrDefault((Map<String, Object>)
         * messageJson.get("options"), new HashMap<>());
         */
        return value == null ? defaultValue : value;
    }

    public boolean checkObjectAndMqttPublish(MqttV5ClientNeo mqttBrokerNeo4jClient, String topic, Object message,
            Map<String, Object> cypherQuerMetadata, Map<String, Object> mqttOptions) {
        Map<String, Object> dbResultObjectFinal = new HashMap();
        if (message instanceof Map) {

            log.debug("mqtt - mqtt checkObjectAndMqttPublish -  message instanceof Map");
            Map<String, Object> dbResultObject = (Map<String, Object>) message;
            for (String key : dbResultObject.keySet()) {
                dbResultObjectFinal.put(key, dbResultObject.get(key));
            }
            cypherQuerMetadata.put("messageType", "instanceOfMap");
        } else if (message instanceof Node) {
            log.debug("mqtt - mqtt checkObjectAndMqttPublish -   message instanceof Node");
            Map<String, Object> dbResultObject = nodeToMap(db, (Node) message);
            // nodes.add(dbResultObject);
            for (String key : dbResultObject.keySet()) {
                dbResultObjectFinal.put(key, dbResultObject.get(key));
            }
            cypherQuerMetadata.put("messageType", "instanceOfNode");
        } else if (message instanceof Relationship) {
            log.debug("mqtt - mqtt checkObjectAndMqttPublish -  message instanceof Relationship");
            Map<String, Object> dbResultObject = relationToMap(db, (Relationship) message);
            // relationships.add(dbResultObject);
            for (String key : dbResultObject.keySet()) {
                dbResultObjectFinal.put(key, dbResultObject.get(key));
            }
            cypherQuerMetadata.put("messageType", "instanceOfRelationship");
        } else {
            try {
                Map<String, Object> dbResultObject = new ObjectMapper().readValue((String) message, Map.class);
                for (String key : dbResultObject.keySet()) {
                    dbResultObjectFinal.put(key, dbResultObject.get(key));
                }
                cypherQuerMetadata.put("messageType", "instanceOfJson");
            } catch (Exception e) {
                log.debug("mqtt - mqtt checkObjectAndMqttPublish -   message instanceof other");
                dbResultObjectFinal.put("value", message.toString());
                cypherQuerMetadata.put("messageType", "instanceOfValue");
            }

        }

        // --- encription required?
        if (!mqttOptions.containsKey("encription")) {
            mqttOptions.put("encription", "none");
        }

        // --- publish message
        try {
            ObjectMapper mapper = new ObjectMapper();
            String dbResultString;
            dbResultString = mapper.writeValueAsString(dbResultObjectFinal).toString();

            // --- encript message and remove metadata
            String encriptionRequest = (String) mqttOptions.get("encription");
            if (encriptionRequest.equals("aes-cbc")) {
                dbResultString = aesCbcCriptDecript.encript(dbResultString, (String) mqttOptions.get("keyBase64"),
                        (String) mqttOptions.get("ivBase64"));
                cypherQuerMetadata = new HashMap<>();
                log.debug("mqtt - mqtt checkObjectAndMqttPublish - encripting");
            }
            // --- publish
            boolean publishStatus = mqttBrokerNeo4jClient.publish(topic, dbResultString, cypherQuerMetadata,
                    mqttOptions);
            // --- log and return
            log.debug("mqtt - mqtt checkObjectAndMqttPublish - published " + encriptionRequest + topic + dbResultString
                    + cypherQuerMetadata + mqttOptions);
            return publishStatus;
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
    }
}