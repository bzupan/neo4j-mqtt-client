##  MQTT Broker: Eclipse Mosquito 2.x  
- run Docker: https://hub.docker.com/_/eclipse-mosquitto 
- setup mosquitto.conf with: 
```
log_dest stdout
allow_anonymous true
connection_messages true
listener 1883
```

##  Node RED 3.x 
- run Docker: https://nodered.org/docs/getting-started/docker
- connect Node RED UI e.g.: http://localhost:1880
- add node-red-contrib-crypto-blue:  https://flows.nodered.org/node/node-red-contrib-crypto-blue
- import neo4j-mqtt-client flow: https://github.com/bzupan/neo4j-mqtt-client/blob/main/test/node-red/node-red_neo4jMqttClientTests.json


##  Neo4j 5.x

