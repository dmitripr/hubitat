# Hubitat Drivers
A collection of personal Hubitat drivers
## MQTT Simple Switch Driver

The driver is in part based on MQTT Link Driver for Hubitat: https://community.hubitat.com/t/release-hubitat-mqtt-link/41846 (https://github.com/mydevbox/hubitat-mqtt-link)

While a few MQTT drivers are available for Hubitat, I found some too complicated and others that won't fit my simple use case. I needed a simple driver that would connect to an MQTT broker and subscribe to a topic, which I can then use to control a simple switch in Hubitat and use it in the Rule Machine to perform actions. My particular use case was for monitoring motion events from a Unifi camera that publishes to MQTT when smart motion (person detected) occurs, which would turn on my porch lights via Hubitat at night. 

This driver can be used for just monitoring an MQTT topic and activating the virtual switch, without the ability to publish back. However, I've built in the ability for two-way communication -- it can also publish back to the same (or another) MQTT topic when the switch in Hubitat is toggled on or off. I also use this driver for controlling a few Tasmota devices via MQTT on my network. 

When setting this up, I recommend using something like MQTT Explorer (https://mqtt-explorer.com/) to see which messages are passing through your broker. 

### Limitations
This driver can only monitor for or publish simple values/payloads (e.g. ON, OFF, 0, 1, etc.) to MQTT topics. There is no capability to parse JSON/XML (at least yet). 

While the driver should survive and reconnect upon Hubitat Hub restart, I've not tested it for cases when the MQTT broker temporarily loses connection, in which case it may not work and may need to be re-initialized (or the Hub restarted to re-initialize multiple devices). 

A virtual device would need to be created for each topic that you want to monitor, which in turn would create a separate connection from Hubitat Hub to the broker.

### Usage
The prompts when adding and setting up the device should be straightforward, but I'll add more details here if others start having recurring issues setting this up. 
