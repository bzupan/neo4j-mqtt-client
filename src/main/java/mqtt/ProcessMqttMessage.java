
package mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Collections;
import java.util.Map;

import org.neo4j.logging.Log;
import org.neo4j.procedure.Context;

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
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.ResourceIterator;

import apoc.util.Util;

// --- ProcessMqttMessage
public class ProcessMqttMessage {

    public Map<String, Object> subscribeOptions = new HashMap<String, Object>();
    public Map<String, Object> options = new HashMap<String, Object>();
    public Log log;
    public GraphDatabaseService db;
    MqttV5ClientNeo mqttBrokerNeo4jClient;
    private static AesCbcCryptDecrypt AesCbcCryptDecrypt = new AesCbcCryptDecrypt();

    public ProcessMqttMessage(GraphDatabaseService dbIn, MqttV5ClientNeo mqttBrokerNeo4jClientIn,
            Map<String, Object> optionsIn, Log logIn) {
        db = dbIn;
        subscribeOptions = optionsIn;
        options = (Map<String, Object>) optionsIn.get("options");
        log = logIn;
        mqttBrokerNeo4jClient = mqttBrokerNeo4jClientIn;
        log.info("mqtt - ProcessMqttMessage registration: " + options.toString());
    }

    public void run(String topic, Map<String, Object> mqttMessageObject) {
        log.debug("mqtt - ProcessMqttMessage: run request received - " + mqttMessageObject);
        // --- get data from mqtt message
        String message = (String) mqttMessageObject.get("payload");
        // --- encryption required?
        if (!options.containsKey("encryption")) {
            options.put("encryption", "none");
        }
        // --- decrypt message
        if ((boolean) options.get("encryption").equals("aes-cbc")) {
            try {
                message = AesCbcCryptDecrypt.decrypt(message, (String) options.get("keyBase64"),
                        (String) options.get("ivBase64"));
                log.info("mqtt - ProcessMqttMessage:  AesCbcCryptDecrypt decrypt OK ");
            } catch (Exception ex) {
                log.error("mqtt - ProcessMqttMessage: AesCbcCryptDecrypt  ERROR " + ex);
                message = "{\"query\":\"AesCbcCryptDecrypt error\"}";
            }
        } else {
            log.info("mqtt - ProcessMqttMessage: message not encrypted - or unknown encryption ");
        }
        // --- debug message
        log.debug("mqtt - ProcessMqttMessage:   " + message + " " + subscribeOptions);
        // --- user props
        Map<String, Object> userProperties = (Map<String, Object>) mqttMessageObject.get("userProperties");
        // --- get response topic - use default
        String responseTopic = getValueOrDefault((String) mqttMessageObject.get("responseTopic"),
                (String) subscribeOptions.get("responseTopicDefault"));
        // --- set correlation data
        String correlationData = getValueOrDefault((String) mqttMessageObject.get("correlationData"), "");
        // ---
        subscribeOptions.remove("lastMessageProcessedResults");
        log.info("mqtt - ProcessMqttMessage run: " + subscribeOptions.toString());
        // --- check message - get key-value
        JSONUtils checkJson = new JSONUtils();
        Map<String, Object> messageJson = (Map<String, Object>) checkJson.jsonStringToMap(message);

        // --- run cypher
        // runCypherQuery(tx, (String) options.get("query"), cypherParams);
        String cypherQuery = "no cypherQuery";
        Map<String, Object> cypherParams = new HashMap<String, Object>();

        // --- get cypherQuery, cypherParams and responseTopic based on
        if (subscribeOptions.get("subscription") == "subscribeCypherRun") {
            cypherQuery = (String) subscribeOptions.get("query");
            cypherParams = messageJson;
            log.info("mqtt - ProcessMqttMessage subscribeCypherRun ... ");
        } else if (subscribeOptions.get("subscription") == "subscribeCypherQuery") {
            cypherQuery = getValueOrDefault((String) messageJson.get("query"), "no cypher query provided");
            cypherParams = getValueOrDefault((Map<String, Object>) messageJson.get("params"), new HashMap<>());
            log.info("mqtt - ProcessMqttMessage subscribeCypherQuery ... ");
        } else {
            cypherQuery = "unknown subscription";
            cypherParams = new HashMap<>();
            responseTopic = "";
            log.error("mqtt - ProcessMqttMessage: ERROR unknown subscription type");
        }

        // --- setup response objects - graph + cypherQueryMetadata
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> relationships = new ArrayList<>();
        Map<String, Object> dbResultObjectFinal = new HashMap<>();
        Map<String, Object> cypherQueryMetadata = new HashMap<>();
        Map<String, Object> graph = new HashMap<>();
        // --- add nodes relations to graph
        graph.put("relationships", relationships);
        graph.put("nodes", nodes);
        // --- add cypherQueryMetadata and graph to result object
        dbResultObjectFinal.put("@graph", graph);
        // --- set metadata
        cypherQueryMetadata.put("neo4jMqttClientId", subscribeOptions.get("neo4jMqttClientId"));
        cypherQueryMetadata.put("subscriptionType", subscribeOptions.get("subscription"));
        cypherQueryMetadata.put("requestTopic", topic);
        cypherQueryMetadata.put("responseTopic", responseTopic);
        cypherQueryMetadata.put("databaseName", db.databaseName());
        cypherQueryMetadata.put("correlationData", correlationData);

        cypherQueryMetadata.put("cypherQuery", cypherQuery);
        cypherQueryMetadata.put("cypherParams", cypherParams);
        cypherQueryMetadata.put("relationships", 0);
        cypherQueryMetadata.put("nodes", 0);
        // cypherQueryMetadata.put("graph", graph);

        try (Transaction tx = db.beginTx()) {
            log.debug("mqtt - ProcessMqttMessage: Transaction beginTx");

            try (Result dbResult = tx.execute(cypherQuery, cypherParams);) {
                log.debug("mqtt - ProcessMqttMessage:  Transaction try ");
                while (dbResult.hasNext()) {
                    log.debug("mqtt - ProcessMqttMessage:  Transaction hasNext ");
                    Map<String, Object> row = dbResult.next();
                    for (String key : dbResult.columns()) {
                        log.debug("mqtt - ProcessMqttMessage: response from db %s = %s%n", key, row.get(key));

                        List<Object> keyValues;

                        try {
                            keyValues = (List<Object>) dbResultObjectFinal.get(key);
                            keyValues.isEmpty();
                            log.debug("mqtt - ProcessMqttMessage:  Transaction key exists " + key);
                        } catch (Exception e) {
                            log.debug("mqtt - ProcessMqttMessage:  Transaction add key " + key);
                            keyValues = new ArrayList<>();
                            dbResultObjectFinal.put((String) key, keyValues);
                        }

                        log.debug("mqtt - ProcessMqttMessage  Transaction hasNext: " + keyValues.toString() + " "
                                + key.toString());

                        if (row.get(key) instanceof Node) {
                            Node node = (Node) row.get(key);
                            Map<String, Object> dbResultObject = nodeToMap(db, node);
                            cypherQueryMetadata.put("nodes", (int) cypherQueryMetadata.get("nodes") + 1);
                            nodes.add(dbResultObject);
                            keyValues.add(dbResultObject);
                        } else if (row.get(key) instanceof Relationship) {
                            Relationship link = (Relationship) row.get(key);
                            Map<String, Object> dbResultObject = relationToMap(db, link);
                            cypherQueryMetadata.put("relationships", (int) cypherQueryMetadata.get("relationships") + 1);
                            relationships.add(dbResultObject);
                            keyValues.add(dbResultObject);
                        } else if (row.get(key) instanceof Map) {
                            keyValues.add(row.get(key));
                        } else {
                            String value = (String) row.get(key).toString();
                            keyValues.add(value);
                        }
                    }
                }
            }
            tx.commit();
            // --- set status
            cypherQueryMetadata.put("cypherQueryStatus", "OK");
            // --- statistics
            subscribeOptions.put("messageReceivedOk", (int) subscribeOptions.get("messageReceivedOk") + 1);
            subscribeOptions.put("lastMessageReceived", message.toString());
            // --- log
            String lastMessageProcessedResults = "mqtt - ProcessMqttMessage "
                    + "\n neo4jMqttClientId: " + subscribeOptions.get("neo4jMqttClientId")
                    + "\n topicRequest: " + topic
                    + "\n responseTopic: " + responseTopic
                    + "\n message: " + message.toString()
                    + "\n cypherQuery: \n " + subscribeOptions.get("query")
                    + "\n cypherParams: \n " + cypherParams.toString()
                    + "\n cypherQuery result:\n " + dbResultObjectFinal.toString()
                    + "\n";
            log.debug("mqtt - ProcessMqttMessage: lastMessageProcessedResults OK - " + lastMessageProcessedResults);

        } catch (Exception ex) {
            // --- setup dbResultObjectFinal object
            dbResultObjectFinal.put("error", ex.toString());
            // --- setup cypherQueryMetadata object
            cypherQueryMetadata.put("cypherQueryStatus", "ERROR");
            // --- statistics
            subscribeOptions.put("messageReceivedError", (int) subscribeOptions.get("messageReceivedError") + 1);
            subscribeOptions.put("lastMessageReceived", message.toString());
            subscribeOptions.put("lastMessageProcessedResults", (String) ex.toString());
            // --- log
            String lastMessageProcessedResults = "mqtt - ProcessMqttMessage "
                    + "\n neo4jMqttClientId: " + subscribeOptions.get("neo4jMqttClientId")
                    + "\n topicRequest: " + topic
                    + "\n responseTopic: " + responseTopic
                    + "\n message: " + message.toString()
                    + "\n cypherQuery: \n " + subscribeOptions.get("query")
                    + "\n cypherParams: \n " + cypherParams.toString()
                    + "\n cypherQuery error:\n " + ex.toString()
                    + "\n";
            log.error("mqtt - ProcessMqttMessage: lastMessageProcessedResults ERROR - " + lastMessageProcessedResults);
        }

        // --- send mqtt message if topic is defined
        if (responseTopic != "") {
            try {

                ObjectMapper mapper = new ObjectMapper();
                String dbResultString = mapper.writeValueAsString(dbResultObjectFinal).toString();

                // --- encrypt message and remove metadata
                String encryptionRequest = (String) options.get("encryption");
                if (encryptionRequest.equals("aes-cbc")) {
                    dbResultString = AesCbcCryptDecrypt.encrypt(dbResultString, (String) options.get("keyBase64"),
                            (String) options.get("ivBase64"));
                    cypherQueryMetadata = new HashMap<>();
                    log.info("mqtt . ProcessMqttMessage  encrypting : ");
                }

                Map<String, Object> mqttPublishOptionsMap = new HashMap();
                if (correlationData != "") {
                    mqttPublishOptionsMap.put("correlationData", correlationData);
                }
                mqttBrokerNeo4jClient.publish((String) responseTopic, (String) dbResultString, cypherQueryMetadata,
                        mqttPublishOptionsMap);
                log.info("mqtt - ProcessMqttMessage: MqTT message send OK");

            } catch (Exception ex) {
                log.error("mqtt - ProcessMqttMessage: ERROR send MqTT message" + ex.toString());
            }
        } else {
            log.info("mqtt - ProcessMqttMessage: no topic defined MqTT message not send");
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

    public static Map<String, Object> nodeToMap(GraphDatabaseService db, Node message) {
        // Map<String, Object> dbResultObject = nodeToMap( db, message)
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
        dbResultObject.put("start", (int) ((Relationship) link).getStartNodeId());
        dbResultObject.put("elementId", (String) ((Relationship) link).getElementId());
        dbResultObject.put("startElementId", (String) ((Relationship) link).getStartNode().getElementId());
        dbResultObject.put("endElementId", (String) ((Relationship) link).getEndNode().getElementId());
        dbResultObject.put("type", (String) ((Relationship) link).getType().toString());
        dbResultObject.put("properties", (Map<String, Object>) ((Relationship) link).getAllProperties());
        dbResultObject.put("database", (String) db.databaseName());
        return dbResultObject;
    }

    // --- JSONUtils
    /**
     * JSONUtils checkJson = new JSONUtils();
     * System.out.print(checkJson.jsonStringToMap(validJson));
     */
    public final static class JSONUtils {

        private JSONUtils() {
        }

        public Object jsonStringToMap(String jsonInString) {
            try {
                Map<String, Object> retMap = new Gson().fromJson(jsonInString.toString(),
                        new TypeToken<HashMap<String, Object>>() {
                        }.getType());
                return retMap;
            } catch (JsonSyntaxException ex) {
                // --- set as object
                Object input = (Object) jsonInString;
                Map<String, Object> returnMap = new HashMap<String, Object>();
                // --- map values
                if (input instanceof String) {
                    returnMap.put("value", (String) jsonInString);
                } else if (input instanceof Integer) {
                    returnMap.put("value", (Integer) input);
                } else if (input instanceof Boolean) {
                    returnMap.put("value", (Boolean) input);
                } else if (input instanceof Float) {
                    returnMap.put("value", (Float) input);
                } else if (input instanceof Double) {
                    returnMap.put("value", (Double) input);
                } else if (input instanceof Map) {
                    returnMap.put("value", (String) input);
                } else {
                    returnMap.put("value", (String) input);
                }
                return returnMap;
            }
        }
    }
}
