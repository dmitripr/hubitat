/**
 *  MQTT Simple Switch Driver
 *
 * MIT License
 *
 * Copyright (c) 2023 Dmitri Prigojev
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

import groovy.json.JsonSlurper
import groovy.json.JsonOutput

public static String version() { return "v0.1.0" }



metadata {
        definition(
        	name: "MQTT Simple Switch Driver",
        	namespace: "dmitripr",
        	author: "Dmitri Prigojev, et al",
        	description: "Driver to subscribe to an MQTT topic and, if needed, provide a two-way communication"
        ) {
		capability "Switch"
        capability "Initialize" //make sure we reconnect on hub restart

		preferences {
			input(
		        name: "brokerIp", 
		        type: "string",
				title: "MQTT Broker IP Address",
				description: "e.g. 192.168.1.200",
				required: true,
				displayDuringSetup: true
			)
			input(
		        name: "brokerPort", 
		        type: "string",
				title: "MQTT Broker Port",
				description: "e.g. 1883",
				required: true,
				displayDuringSetup: true,
                default: "1883"
			)

		    input(
                name: "subscription_topic", 
		        type: "string",
				title: "Topic",
				description: "Topic that will be subscribed to/monitored for this device e.g. cmnd/tasmota/device___/power",
				required: true,
				displayDuringSetup: true
            )
            
            input(
                name: "switch_on_value", 
		        type: "string",
				title: "MQTT Value for Switch On",
				description: "Value that will turn the virtual switch of this device ON",
				required: true,
				displayDuringSetup: true
            )
            
            input(
                name: "switch_off_value", 
		        type: "string",
				title: "MQTT Value for Switch Off",
				description: "Value that will turn the virtual switch of this device OFF",
				required: true,
				displayDuringSetup: true
            )

            input(
                name: "allowTwoWay", 
                type: "bool", 
                title: "Enable publishing back",
                description: "Allows the virtual device to publish back to the MQTT topic based on action taken (On or Off)",
                required: false, 
                default: false
            )
            
            input(
                name: "publish_topic", 
		        type: "string",
				title: "Topic to publish to",
				description: "Topic that will be published to for this device, if different from subscribe e.g. cmnd/tasmota/device___/power",
				required: false,
				displayDuringSetup: true
            )
            
            input(
                name: "switch_on_publish_value", 
		        type: "string",
				title: "MQTT Value to publish for Switch On",
				description: "Value that will be published when this virtual device is switched ON",
				required: false,
				displayDuringSetup: true
            )
            
            input(
                name: "switch_off_publish_value", 
		        type: "string",
				title: "MQTT Value to publish for Switch Off",
				description: "Value that will be published when this virtual device is switched OFF",
				required: false,
				displayDuringSetup: true
            )
            
            input(
		        name: "brokerUser", 
		        type: "string",
				title: "MQTT Broker Username",
				description: "e.g. mqtt_user",
				required: false,
				displayDuringSetup: true
			)
		    input(
		        name: "brokerPassword", 
		        type: "password",
				title: "MQTT Broker Password",
				description: "e.g. ^L85er1Z7g&%2En!",
				required: false,
				displayDuringSetup: true
			)

            
            input(
                name: "debugLogging", 
                type: "bool", 
                title: "Enable debug logging", 
                required: false, 
                default: false
            )
		}

        // Provided for broker setup and troubleshooting
		command "publish", [[name:"topic*",type:"STRING", title:"test",description:"Topic"],[name:"message",type:"STRING", description:"Message"]]
		command "connect"
		command "disconnect"
    }
}

void initialize() {
    debug("Initializing driver...")
    
    device_ID = device.deviceId
    
    try {   
        interfaces.mqtt.connect(getBrokerUri(),
                           "hubitat_${device_ID}", //add device ID to make unique
                           settings?.brokerUser, 
                           settings?.brokerPassword)
       
        // delay for connection
        pauseExecution(1000)
        
        // subscribe to the topic of this driver
        subscribe()
        connected()
        
    } catch(Exception e) {
        error("[d:initialize] ${e}")
    }
}

// reconnect on saved changes
def updated() {
    disconnect()
    connect()
}


def on() {
    if (allowTwoWay) {
        publishMqtt(settings?.publish_topic, settings?.switch_on_publish_value)
        sendEvent(name: "switch", value: "on", descriptionText: "${device.displayName} switch is on")
    }
}

def off() {
    if (allowTwoWay) {
        publishMqtt(settings?.publish_topic, settings?.switch_off_publish_value)
        sendEvent(name: "switch", value: "off", descriptionText: "${device.displayName} switch is off")
    }
}

// ========================================================
// MQTT COMMANDS
// ========================================================

def publish(topic, payload) {
    publishMqtt(topic, payload)
}

def subscribe() {
    if (notMqttConnected()) {
        connect()
    }

    debug("[d:subscribe] full topic: ${settings?.subscription_topic}")
    interfaces.mqtt.subscribe(settings?.subscription_topic)
    state.subscription_topic = settings?.subscription_topic
    state.publishing_topic = settings?.publish_topic
}

def unsubscribe() {
    if (notMqttConnected()) {
        connect()
    }
    
    debug("[d:unsubscribe] full topic: ${settings?.subscription_topic}")
    interfaces.mqtt.unsubscribe(settings?.subscription_topic)
}

def connect() {
    initialize()
}

def disconnect() {
    try {
        unsubscribe()
        interfaces.mqtt.disconnect()   
        disconnected()
    } catch(e) {
        warn("Disconnection from broker failed", ${e.message})
        if (interfaces.mqtt.isConnected()) connected()
    }
}


// ========================================================
// MQTT METHODS
// ========================================================

// Parse incoming message from the MQTT broker
def parse(String event) {
    def message = interfaces.mqtt.parseMessage(event)  
    
    debug("[d:parse] Received MQTT message: ${message}")
    
    if (message.payload == settings?.switch_on_value) {
        sendEvent(name: "switch", value: "on", descriptionText: "${device.displayName} switch is on")
    }
    
    if (message.payload == settings?.switch_off_value) {
        sendEvent(name: "switch", value: "off", descriptionText: "${device.displayName} switch is off")
    }
    
    
    state.Subscription_topic_value = message.payload
    return sendEvent(name: "Subscription_topic_value", value: message.payload, displayed: true)
}

def mqttClientStatus(status) {
    debug("[d:mqttClientStatus] status: ${status}")
}

def publishMqtt(topic, payload, qos = 0, retained = false) {
    if (notMqttConnected()) {
        debug("[d:publishMqtt] not connected")
        initialize()
    }
    
    def pubTopic = "${topic}"

    try {
        interfaces.mqtt.publish("${pubTopic}", payload, qos, retained)
        debug("[d:publishMqtt] topic: ${pubTopic} payload: ${payload}")
        
    } catch (Exception e) {
        error("[d:publishMqtt] Unable to publish message: ${e}")
    }
}

// ========================================================
// ANNOUNCEMENTS
// ========================================================

def connected() {
    debug("[d:connected] Connected to broker")
    sendEvent (name: "connectionState", value: "connected")
}

def disconnected() {
    debug("[d:disconnected] Disconnected from broker")
    sendEvent (name: "connectionState", value: "disconnected")
}


// ========================================================
// HELPERS
// ========================================================

def normalize(name) {
    return name.replaceAll("[^a-zA-Z0-9]+","-").toLowerCase()
}

def getBrokerUri() {        
    return "tcp://${settings?.brokerIp}:${settings?.brokerPort}"
}

def getHubId() {
    def hub = location.hubs[0]
    def hubNameNormalized = normalize(hub.name)
    return "${hubNameNormalized}-${hub.hardwareID}".toLowerCase()
}


def mqttConnected() {
    return interfaces.mqtt.isConnected()
}

def notMqttConnected() {
    return !mqttConnected()
}

// ========================================================
// LOGGING
// ========================================================

def debug(msg) {
	if (debugLogging) {
    	log.debug msg
    }
}

def info(msg) {
    log.info msg
}

def warn(msg) {
    log.warn msg
}

def error(msg) {
    log.error msg
}