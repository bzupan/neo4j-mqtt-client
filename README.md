# Neo4j MqTT v5 Client
Noo4j branch: 5.x  
root: https://github.com/bzupan/neo4j-mqtt-client 

Neo4j Graph Database MqTT v5 Client Functions and Procedures:
- Publishing MqTT Messages from Neo4j Graph Database
- Subscribing to and Processing MqTT Messages in Neo4j Graph Database

## Notes
- Utilizes the HiveMQ MqTT Client for Java (https://www.hivemq.com/article/mqtt-client-library-enyclopedia-hivemq-mqtt-client/)
- Tested with Eclipse Mosquitto 2.0.18 MqTT Broker  (https://mosquitto.org/)
- Tested with Neo4j 5.13 Community Eddition Graph Database
- Requires Neo4j 5 APOC plugin (APOC 5.x neeeds to be installed)
- MqTT version 5 only 
- Currently only TCP transfer protocol implemented
- [Node RED test flows](test/node-red/node-red_neo4jMqttClientTests.json) are prepered for examles demonstrated in this README

# Managing Neo4j MqTT Clients and Connections
The process involves registering the Neo4j MqTT broker client and establishing a connection to the MqTT broker.
Once the client is connected, MqTT messages can be published or subscribed to.

Neo4j MqTT broker client functions
- connectBroker, 
- listBrokers and 
- disconnectBroker.

## Register Neo4j MqTT Client and Connect the MqTT Broker
CYPHER query
```cypher
RETURN mqtt.connectBroker(
    'neo4jMqttClientId',          // Unique ID of the MqTT Client
    {
        serverHost:'localhost',   // MqTT Broker IP Address
        serverPort:1883           // MqTT Broker Port
    }
)
```

## List Neo4j MqTT Clients/Connections and Show Details
CYPHER query
```cypher
RETURN mqtt.listBrokers()
```

## Disconnect MqTT Broker and Unregister Neo4j MqTT Client
CYPHER query
```cypher
RETURN mqtt.disconnectBroker(
  'neo4jMqttClientId'           // Neo4j MqTT Client ID to Disconnect
)
```
# Generate AES-CBC Encryption Key (key) and Initialization Vector (iv)
Neo4j MqTT broker client supports AES-CBC payload encription. Base64 encoded key and iv is needed. Utility function "generateAesCbcKeyIv" will generate 256 AES-CBC key and iv for symetric encription - see examples of using provided keys.

CYPHER query
```cypher
RETURN mqtt.generateAesCbcKeyIv()
```
Sample Response
```json
{
  "ivBase64": "7CCPV/M/BQMsM2c1zWvpsKFEA+dAeqMIFeWSs1Sfkjw=",
  "keyHex": "7bf17ec2679f36c1e40b385791a35bc2de323a3ff14b7af763a585e47bb97128",
  "keyText": "{�~�g�6��\u000b8W��[��2:?�Kz�c���{�q(",
  "ivText": "� �W�?\u0005\u0003,3g5�k鰡D\u0003�@z�\u0015咳T��<",
  "keyBase64": "e/F+wmefNsHkCzhXkaNbwt4yOj/xS3r3Y6WF5Hu5cSg=",
  "ivHex": "ec208f57f33f05032c336735cd6be9b0a14403e7407aa30815e592b3549f923c"
}
```
# Publish MqTT Messages Using Neo4j MqTT Client
Neo4j MqTT CYPHER function and procedure for publishing MqTT v5 messages:
- publishMessagefunction - for publishing Neo4j objects (map, node and relation). If list is provided MqTT message will be send for each element - see examples!
- publishGrph procedure - run CYPHER query and publish Neo4j graph. Graph will be exported to JSON nodes relationships object - see examples!

## Neo4j publishMessage Function
Following CYPHER query will publish simple MqTT message.
CYPHER query
```cypher
// --- publish simple map object "{message:123}" to "mqtt/topic/path" topic utilazing 'neo4jMqttClientId' Neo4j MqTT Client 
RETURN mqtt.publishMessage(
    'neo4jMqttClientId',        // Neo4j MqTT Client ID
    'mqtt/topic/path',          // topic to publish to
    {message:123}               // payload
)
```
Response to CYPHER query
```json
{
  "topic": "mqtt/topic/path",
  "message": {
    "message": 123
  },
  "status": "OK",
  "neo4jMqttClientId": "neo4jMqttClientId",
  "statusMessage": "MqTT Publish OK"
}
```
Received MqTT Message on 'mqtt/topic/path' 
```json
{
  "topic": "mqtt/topic/path",
  "payload": {
    "message": 123
  },
  "qos": 0,
  "retain": false,
  "userProperties": {
    "databaseName": "neo4j",
    "messageType": "instanceOfMap",
    "requestMqttTopic": "mqtt/topic/path",
    "neo4jMqttClientId": "neo4jMqttClientId"
  }
}

```

![CYPHER MqTT publish](img/cypher-mqtt-publish.PNG)

![CYPHER MqTT publish receive](img/cypher-mqtt-publish-received.PNG)  

Following CYPHER query will publish simple MqTT message adding responseTopic and correlationData options to the MqTT v5 message
CYPHER query
```cypher
// --- Publish MqTT Message with MqTT v5 options "responseTopic" and "correlationData"
RETURN mqtt.publishMessage(
    'neo4jMqttClientId', 
    'mqtt/topic/path', 
    {message:123}, 
    {                                              // MqTT v5 options map
        responseTopic:"mqtt/topic/path/response",
        correlationData:"messageId-123" 
    }
)
```
Received MqTT Message on 'mqtt/topic/path' 
```json
{
  "topic": "mqtt/topic/path",
  "payload": {
    "message": 123
  },
  "qos": 0,
  "retain": false,
  "responseTopic": "mqtt/topic/path/response",
  "correlationData": [
    109,
    101,
    115,
    115,
    97,
    103,
    101,
    73,
    100,
    45,
    49,
    50,
    51
  ],
  "userProperties": {
    "databaseName": "neo4j",
    "messageType": "instanceOfMap",
    "requestMqttTopic": "mqtt/topic/path",
    "neo4jMqttClientId": "neo4jMqttClientId"
  }
}
```

AES-CBC payload encription is possible with additional paramateres in the options map.  
Message will be published as Base6 encoded string - all userProps will be remowed from the message!

CYPHER query
```cypher
// --- AES-CBC encript MqTT payload and publish message to the mqtt/topic/pathEncripted topic
RETURN mqtt.publishMessage(
    'neo4jMqttClientId', 
    'mqtt/topic/pathEncripted', 
    {message:123}, 
    {
        encription: "aes-cbc",                    // currently only aes-cbc is supported
        ivBase64: "FnAxDoCHpgHkrZr3jRGmbA==",     // Base64 encoded iv 
        keyBase64: "2ggLKL4wxTwmZQ8kPMCT8A=="     // Base64 encoded key
    }  
)
```
Received MqTT Message on 'mqtt/topic/pathEncripted' 
```json
{
  "topic": "mqtt/topic/pathEncripted",
  "payload": "+CYr1PLLU9PtBMNRTFqDDg==",
  "qos": 0,
  "retain": false
}
```

We can pass node or relation to the message - node / relation will be pubished as JSON - see examples
CYPHER query with node as payload
```cypher
// --- publish single node as JSON document to 'mqtt/topic/path'
MERGE (s:MqttTestNode {someProp:"startNode"})-[l:MQTT_TEST {someProp:"linkProp"}]->(e:MqttTestNode {someProp:"endNode"})
RETURN mqtt.publishMessage(
  'neo4jMqttClientId', 
  'mqtt/topic/path', 
  s
)
```
Received MqTT Message on 'mqtt/topic/path' 
```json
{
  "topic": "mqtt/topic/path",
  "payload": {
    "elementId": "4:6fd8bab9-128b-4f8a-adc3-6ea50ea8e2d0:29",
    "database": "neo4j",
    "identity": 29,
    "properties": {
      "someProp": "startNode"
    },
    "labels": [
      "MqttTestNode"
    ]
  },
  "qos": 0,
  "retain": false,
  "userProperties": {
    "databaseName": "neo4j",
    "messageType": "instanceOfNode",
    "requestMqttTopic": "mqtt/topic/path",
    "neo4jMqttClientId": "neo4jMqttClientId"
  }
}
```
CYPHER query with relation as payload
```cypher
// --- publish single node as JSON document to 'mqtt/topic/path'
MERGE (s:MqttTestNode {someProp:"startNode"}) -[l:MQTT_TEST {someProp:"linkProp"}]->(e:MqttTestNode {someProp:"endNode"})
RETURN mqtt.publishMessage(
  'neo4jMqttClientId', 
  'mqtt/topic/path', 
  l
)
```
Received MqTT Message on 'mqtt/topic/path' 
```json
{
  "topic": "mqtt/topic/path",
  "payload": {
    "elementId": "5:6fd8bab9-128b-4f8a-adc3-6ea50ea8e2d0:1",
    "database": "neo4j",
    "strt": 29,
    "identity": 1,
    "endElementId": "4:6fd8bab9-128b-4f8a-adc3-6ea50ea8e2d0:30",
    "end": 30,
    "satartElementId": "4:6fd8bab9-128b-4f8a-adc3-6ea50ea8e2d0:29",
    "type": "MQTT_TEST",
    "properties": {
      "someProp": "linkProp"
    }
  },
  "qos": 0,
  "retain": false,
  "userProperties": {
    "databaseName": "neo4j",
    "messageType": "instanceOfRelationship",
    "requestMqttTopic": "mqtt/topic/path",
    "neo4jMqttClientId": "neo4jMqttClientId"
  }
}
```

If stream is passed - for every object MqTT messages will be send
```cypher
// --- publish list of nodes JSON document to 'mqtt/topic/path' - please note for every node in the list MqTT message will be send
MATCH (n:MqttTestNode)  
RETURN mqtt.publishMessage(
  'neo4jMqttClientId', 
  'mqtt/topic/path', 
  n
)
```

We can export graph data as JSON using APOC export json query - please note APOC node relations JSON export format differes from our node relation format!
CYPHER query with APOC export
```cypher
// --- publish graph using APOC JSON export 
CALL apoc.export.json.query(
    'MATCH (s:MqttTestNode)-[l:MQTT_TEST]->(e:MqttTestNode) RETURN *',
    null,
    {stream: true}
)
YIELD  data AS apocJsonGraph
RETURN mqtt.publishMessage(
    'neo4jMqttClientId', 
    'mqtt/topic/path', 
    apocJsonGraph
)
```
Received MqTT Message on 'mqtt/topic/path' 
```json
{
  "topic": "mqtt/topic/path",
  "payload": {
    "s": {
      "type": "node",
      "id": "4",
      "labels": [
        "MqttTestNode"
      ],
      "properties": {
        "someProp": "startNode"
      }
    },
    "e": {
      "type": "node",
      "id": "5",
      "labels": [
        "MqttTestNode"
      ],
      "properties": {
        "someProp": "endNode"
      }
    },
    "l": {
      "type": "relationship",
      "id": "2",
      "label": "MQTT_TEST",
      "properties": {
        "someProp": "linkProp"
      },
      "start": {
        "id": "4",
        "labels": [
          "MqttTestNode"
        ],
        "properties": {
          "someProp": "startNode"
        }
      },
      "end": {
        "id": "5",
        "labels": [
          "MqttTestNode"
        ],
        "properties": {
          "someProp": "endNode"
        }
      }
    }
  },
  "qos": 0,
  "retain": false,
  "userProperties": {
    "databaseName": "neo4j",
    "messageType": "instanceOfJson",
    "requestMqttTopic": "mqtt/topic/path",
    "neo4jMqttClientId": "neo4jMqttClientId"
  }
}
```

We can setup APOC triger which will send MqTT messages upon database transactions
* to setup trigger see: https://neo4j.com/docs/apoc/current/background-operations/triggers/#_list_of_triggers
```
# setup apoc.conf with 
apoc.trigger.enabled=true
apoc.trigger.refresh=600

# setup neo4j.conf
dbms.security.procedures.unrestricted=apoc.*

# restart Neo4j
```
CYPHER query to register triger which will send MqTT message on node creation
```cypher
:use system
CALL apoc.trigger.install(
    'neo4j',
    'send-mqtt-message-when-new-node',
    'UNWIND $createdNodes AS n WITH n RETURN mqtt.publishMessage("neo4jMqttClientId", "mqtt/topic/path", n)',
    {phase: 'afterAsync'}    
);
:use neo4j
```

CYPHER which will create new node and trigger MqTT message
```cypher
// --- test
CREATE (n:MqttTestTrigger)
RETURN n
```

Mqtt message received when new node is created
```json
{
  "topic": "mqtt/topic/path",
  "payload": {
    "elementId": "4:6fd8bab9-128b-4f8a-adc3-6ea50ea8e2d0:31",
    "database": "neo4j",
    "identity": 31,
    "properties": {},
    "labels": [
      "MqttTestTrigger"
    ]
  },
  "qos": 0,
  "retain": false,
  "userProperties": {
    "databaseName": "neo4j",
    "messageType": "instanceOfNode",
    "requestMqttTopic": "mqtt/topic/path",
    "neo4jMqttClientId": "neo4jMqttClientId"
  }
}
```

## Neo4j publishGrph Procedure
CYPHER query which will "publish" graph as single MqTT message - see response 
```cypher
// --- publish Neo4j graph using CYPHER query with params + MqTT v5 "goodies"
CALL mqtt.publishGrph(
  'neo4jMqttClientId', 
  'mqtt/topic/path',
  'MERGE (s:MqttTestNode {someProp:"startNode"}) -[l:MQTT_TEST {someProp:"linkProp"}]->(e:MqttTestNode {someProp:"endNode"}) RETURN *',    // CYPHER query
  {message:123},      // CYPHER query params
  {
    responseTopic:"mqtt/topic/path/response"
  }
)
```
Mqtt message received
```json
{
  "topic": "mqtt/topic/path",
  "payload": {
    "relationships": [
      {
        "elementId": "5:6fd8bab9-128b-4f8a-adc3-6ea50ea8e2d0:1",
        "database": "neo4j",
        "strt": 29,
        "identity": 1,
        "endElementId": "4:6fd8bab9-128b-4f8a-adc3-6ea50ea8e2d0:30",
        "end": 30,
        "satartElementId": "4:6fd8bab9-128b-4f8a-adc3-6ea50ea8e2d0:29",
        "type": "MQTT_TEST",
        "properties": {
          "someProp": "linkProp"
        }
      }
    ],
    "nodes": [
      {
        "elementId": "4:6fd8bab9-128b-4f8a-adc3-6ea50ea8e2d0:30",
        "database": "neo4j",
        "identity": 30,
        "properties": {
          "someProp": "endNode"
        },
        "labels": [
          "MqttTestNode"
        ]
      },
      {
        "elementId": "4:6fd8bab9-128b-4f8a-adc3-6ea50ea8e2d0:29",
        "database": "neo4j",
        "identity": 29,
        "properties": {
          "someProp": "startNode"
        },
        "labels": [
          "MqttTestNode"
        ]
      }
    ]
  },
  "qos": 0,
  "retain": false,
  "responseTopic": "mqtt/topic/path/response",
  "userProperties": {
    "cypherQuery": "MERGE (s:MqttTestNode {someProp:\"startNode\"}) -[l:MQTT_TEST {someProp:\"linkProp\"}]->(e:MqttTestNode {someProp:\"endNode\"}) RETURN *",
    "relationships": "1",
    "nodes": "2",
    "databaseName": "neo4j",
    "cypherParams": "{message=123}",
    "neo4jMqttClientId": "neo4jMqttClientId",
    "publishTopic": "mqtt/topic/path",
    "cypherQueryStatus": "OK"
  }
}
```

# Subscribe to MqTT Messages and run CYPHER Queries
Two Neo4j MqTT subscription procedures are aviable:
- subscribeCypherQuery - kind of MqTT "BOLT" protocol for the execution of database queries
- subscribeCypherRun - predefined CYPHER queries will be executed upon MqTT message receive - message will be populated as query params to the CYPHER query defined by the subscription.

## Neo4j subscribeCypherQuery Procedure
Following CYPHER Query will subscribe Neo4j Client to the provided topic and process encripted messages as query requests
request should be in format query, params - see example
```cypher
// --- listen on 'neo4j/cypherQuery/requestEncripted' with an default response topic passed as "responseTopic"
CALL mqtt.subscribeCypherQuery(
  'neo4jMqttClientId',               // Neo4j MqTT Client ID
  'neo4j/cypherQuery/requestEncripted',       // topic to listen for CYPHER query requests in format {query: ""cypher Query", params: {} }
  {                                  // options map with default response topis where query results will be published
      responseTopic:'neo4j/cypherQuery/resultsDefault',  // default response topic
      encription: "aes-cbc",                    // currently only aes-cbc is supported
      ivBase64: "FnAxDoCHpgHkrZr3jRGmbA==",     // Base64 encoded iv 
      keyBase64: "2ggLKL4wxTwmZQ8kPMCT8A=="     // Base64 encoded key
  }
) 
```

Graph MqTT CYPHER Query Request message send on "neo4j/cypherQuery/request" topic will trigger ....
```json
{
  "topic": "neo4j/cypherQuery/request",
  "payload": {
    "query": "MERGE (n:MqttTestNode) ON CREATE SET n.count=1, n.message=$message ON MATCH SET n.count = n.count +1, n.message=$message WITH n MERGE (n)-[l:MQTT_TEST]->(m:MqttTestNode) RETURN *",
    "params": {
      "message": "setup message"
    }
  },
  "qos": 0,
  "retain": false,
  "responseTopic": "neo4j/cypherQuery/results"
}
```

.... MqTT message received on "neo4j/cypherQuery/results" topic.
```json

{
  "topic": "neo4j/cypherQuery/results",
  "payload": {
    "@graph": {
      "relationships": [
        {
          "elementId": "5:6fd8bab9-128b-4f8a-adc3-6ea50ea8e2d0:1",
          "database": "neo4j",
          "strt": 1,
          "identity": 1,
          "endElementId": "4:6fd8bab9-128b-4f8a-adc3-6ea50ea8e2d0:3",
          "end": 3,
          "satartElementId": "4:6fd8bab9-128b-4f8a-adc3-6ea50ea8e2d0:1",
          "type": "MQTT_TEST",
          "properties": {}
        }
      ],
      "nodes": [
        {
          "elementId": "4:6fd8bab9-128b-4f8a-adc3-6ea50ea8e2d0:3",
          "database": "neo4j",
          "identity": 3,
          "properties": {},
          "labels": [
            "MqttTest"
          ]
        },
        {
          "elementId": "4:6fd8bab9-128b-4f8a-adc3-6ea50ea8e2d0:1",
          "database": "neo4j",
          "identity": 1,
          "properties": {
            "count": 1,
            "message": "setup message"
          },
          "labels": [
            "MqttTest"
          ]
        }
      ]
    },
    "l": [
      {
        "elementId": "5:6fd8bab9-128b-4f8a-adc3-6ea50ea8e2d0:1",
        "database": "neo4j",
        "strt": 1,
        "identity": 1,
        "endElementId": "4:6fd8bab9-128b-4f8a-adc3-6ea50ea8e2d0:3",
        "end": 3,
        "satartElementId": "4:6fd8bab9-128b-4f8a-adc3-6ea50ea8e2d0:1",
        "type": "MQTT_TEST",
        "properties": {}
      }
    ],
    "m": [
      {
        "elementId": "4:6fd8bab9-128b-4f8a-adc3-6ea50ea8e2d0:3",
        "database": "neo4j",
        "identity": 3,
        "properties": {},
        "labels": [
          "MqttTestNode"
        ]
      }
    ],
    "n": [
      {
        "elementId": "4:6fd8bab9-128b-4f8a-adc3-6ea50ea8e2d0:1",
        "database": "neo4j",
        "identity": 1,
        "properties": {
          "count": 1,
          "message": "setup message"
        },
        "labels": [
          "MqttTestNode"
        ]
      }
    ]
  },
  "qos": 0,
  "retain": false,
  "userProperties": {
    "correlationData": "correlationId-test01",
    "cypherQuery": "MERGE (n:MqttTestNode) ON CREATE SET n.count=1, n.message=$message ON MATCH SET n.count = n.count +1, n.message=$message WITH n MERGE (n)-[l:MQTT_TEST]->(m:MqttTestNode) RETURN *",
    "relationships": "1",
    "subsctiptionType": "subscribeCypherQuery",
    "nodes": "2",
    "databaseName": "neo4j",
    "cypherParams": "{message=setup message}",
    "responseTopic": "neo4j/cypherQuery/results",
    "requesTopic": "neo4j/cypherQuery/request",
    "neo4jMqttClientId": "neo4jMqttClientId",
    "cypherQueryStatus": "OK"
  }
}
```

Following CYPHER Query will subscribe Neo4j Client to the provided topic and process received messages as query requests
request should be in format query, params - see example
```cypher
// --- listen on 'neo4j/cypherQuery/request' with an default response topic passed as "responseTopic"
CALL mqtt.subscribeCypherQuery(
  'neo4jMqttClientId',               // Neo4j MqTT Client ID
  'neo4j/cypherQuery/request',       // topic to listen for CYPHER query requests in format {query: ""cypher Query", params: {} }
  {                                  // options map with default response topis where query results will be published
      responseTopic:'neo4j/cypherQuery/resultsDefault'
  }
) 
```


## Neo4j subscribeCypherRun Procedure 
Predefined CYPHER queries will be executed upon MqTT message receive - message will be populated as query params to the query stored by the subscription.
```cypher
// --- listen on 'neo4j/cypherQuery/request' with an default response topics passed as option "responseTopic"
CALL mqtt.subscribeCypherRun(
  'neo4jMqttClientId', 
  'neo4j/cypherRun/request',
  'MERGE (n:MqttTestSubscribe) ON CREATE SET n.count=1, n.message=$message ON MATCH SET n.count = n.count +1, n.message=$message RETURN n', // CYPHER query - query params will be received by the message
  {                 // options map with default response topis where query results will be published
    responseTopic:'neo4j/cypherRun/resultsDefault'
  } 
)
```

Following MqTT message send to "neo4j/cypherRun/request" will trigger ...
```json

{
  "topic": "neo4j/cypherRun/request",
  "payload": {
    "message": "response goes to provided topic"
  },
  "qos": 0,
  "retain": false,
  "responseTopic": "neo4j/cypherRun/results"
}
```
... MqTT message receive on "neo4j/cypherRun/results"
```json
{
  "topic": "neo4j/cypherRun/results",
  "payload": {
    "@graph": {
      "relationships": [],
      "nodes": [
        {
          "elementId": "4:6fd8bab9-128b-4f8a-adc3-6ea50ea8e2d0:32",
          "database": "neo4j",
          "identity": 32,
          "properties": {
            "count": 8,
            "message": "response goes to provided topic"
          },
          "labels": [
            "MqttTestSubscribe"
          ]
        }
      ]
    },
    "n": [
      {
        "elementId": "4:6fd8bab9-128b-4f8a-adc3-6ea50ea8e2d0:32",
        "database": "neo4j",
        "identity": 32,
        "properties": {
          "count": 8,
          "message": "response goes to provided topic"
        },
        "labels": [
          "MqttTestSubscribe"
        ]
      }
    ]
  },
  "qos": 0,
  "retain": false,
  "userProperties": {
    "correlationData": "id123",
    "cypherQuery": "MERGE (n:MqttTestSubscribe) ON CREATE SET n.count=1, n.message=$message ON MATCH SET n.count = n.count +1, n.message=$message RETURN n",
    "relationships": "0",
    "subsctiptionType": "subscribeCypherRun",
    "nodes": "1",
    "databaseName": "neo4j",
    "cypherParams": "{message=response goes to provided topic}",
    "responseTopic": "neo4j/cypherRun/results",
    "requesTopic": "neo4j/cypherRun/request",
    "neo4jMqttClientId": "neo4jMqttClientId",
    "cypherQueryStatus": "OK"
  }
}
```

... or sample error MqTT message receive on "neo4j/cypherRun/results"
```json
{
  "topic": "neo4j/cypherRun/results",
  "payload": {
    "@graph": {
      "relationships": [],
      "nodes": []
    },
    "error": "org.neo4j.graphdb.QueryExecutionException: Expected parameter(s): message"
  },
  "qos": 0,
  "retain": false,
  "userProperties": {
    "correlationData": "id123",
    "cypherQuery": "MERGE (n:MqttTestSubscribe) ON CREATE SET n.count=1, n.message=$message ON MATCH SET n.count = n.count +1, n.message=$message RETURN n",
    "relationships": "0",
    "subsctiptionType": "subscribeCypherRun",
    "nodes": "0",
    "databaseName": "neo4j",
    "cypherParams": "{noMessage=123.0}",
    "responseTopic": "neo4j/cypherRun/results",
    "requesTopic": "neo4j/cypherRun/request",
    "neo4jMqttClientId": "neo4jMqttClientId",
    "cypherQueryStatus": "ERROR"
  }
}
```