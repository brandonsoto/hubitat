/***************************************************************************************************
 * MIT License
 *
 * Copyright (c) 2023 Brandon Soto
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:

 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.

 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
***************************************************************************************************/

metadata {
    definition (name: "Govee Light Controller", namespace: "bsoto", author: "Brandon Soto", importUrl: "") {
        capability "Initialize"
        capability "Refresh"
        capability "Configuration"
        attribute "serverStatus", "enum", ["Disconnected", "Disconnecting", "Connected"]
    }

    preferences {
        input name: "goveeServerAddress", type: "text", title: "Govee Server's IP Address", description: "The Govee Server's IP address", required: true
        input name: "goveeServerPort", type: "number", title: "Govee Server's Port", description: "The Govee Server's port", required: true, defaultValue: 4003
        input name: "devicesPollInterval", type: "number", title: "Interval for polling for available Govee devices (in seconds)", defaultValue: 600
        // input name: "pollInterval", type: "enum", title: "Set the Poll Interval.", options: [0:"off", 60:"1 minute", 120:"2 minutes", 180:"3 minutes", 300:"5 minutes",600:"10 minutes",900:"15 minutes",1800:"30 minutes",3600:"60 minutes"], required: true, defaultValue: "600"
        input name: "debugOutput", type: "bool", title: "Enable debug logging?", defaultValue: false
    }
}

def logDebug(String message) {
    if (debugOutput) {
        log.debug(message)
    }
}

def webSocketStatus(String message) {
    // TODO: this function is not thread-safe
    logDebug("webSocketStatus: +")
    logDebug("webSocketStatus: Got web socket status ${message}")
    if (message.startsWith("status: open")) {
        log.info "webSocketStatus: Connected to Govee server"
        state.reconnectDelay = 1
        sendEvent(name: "serverStatus", value: "Connected")
        refresh()
    } else if (message.startsWith("status: closing")) {
        def unexpectedClose = !state.wasExpectedClose
        log.info "webSocketStatus: Closing connection to Govee server! unexpected=$unexpectedClose, waitingForSocketStatus=$waitingForSocketStatus"
        sendEvent(name: "serverStatus", value: "Disconnecting")
        if (unexpectedClose && !state.waitingForSocketStatus) {
            reinitialize()
        }
    } else if (message.startsWith("failure:")) {
        log.info "webSocketStatus: Failed to connect to Govee server! $message"
        sendEvent(name: "serverStatus", value: "Disconnected")
        reinitialize()
    }
    state.waitingForSocketStatus = false
    setWasExpectedClose(false)
    logDebug("webSocketStatus: -")
}

def initialize() {
    logDebug("initialize +")
    unschedule()

    setWasExpectedClose(false)
    state.devices = new ArrayList()

    closeEventSocket()

    log.info "Initializing Govee devices..."
    try {
        def address = "ws://$goveeServerAddress:$goveeServerPort"
        logDebug("Connecting to Govee server at $address")
        state.waitingForSocketStatus = true
        interfaces.webSocket.connect(address, pingInterval: 60, ignoreSSLIssues: true, headers:["Content-Type": "text/plain"])
    } catch (e) {
        log.warn "Failed to connect to web socket - $e"
    }
    logDebug("initialize -")
}

private def reinitialize() {
    logDebug("reinitialize +")
    // first delay is 2 seconds, doubles every time
    def delayCalc = (state.reconnectDelay ?: 1) * 2
    // upper limit is 1800s (30 min)
    def reconnectDelay = delayCalc <= 1800 ? delayCalc : 1800
    state.reconnectDelay = reconnectDelay
    log.info "Retrying connect to Govee server in $reconnectDelay seconds"
    runIn(reconnectDelay, "initialize")
    logDebug("reinitialize -")
}

def configure() {
    logDebug("configure +")
    state.clear()
    initialize()
    logDebug("configure -")
}

def updated() {
    logDebug("updated +")
    refresh()
    logDebug("updated -")
}

private def disconnect() {
    log.info "Closing Govee server socket"
    try {
        interfaces.webSocket.close()
    } catch (e) {
        log.warn "Failed to close socket - $e"
    }
}

def installed() {
    logDebug("installed +")
    initialize()
    logDebug("installed -")
}

def uninstalled() {
    logDebug("uninstalled +")
    unschedule()
    closeEventSocket()
    logDebug("uninstalled -")
}

def parse(String description) {
    logDebug("parse: description=$description")

    byte[] bytes = description.decodeHex()
    logDebug("parse: decodedHex - $bytes")
    def payload = null;
    try {
        payload = new groovy.json.JsonSlurper().parseText(new String(bytes, "UTF-8"))
        logDebug("parse: payload=$payload")

        if (payload == null){
            log.warn "does not contain payload"
            return
        } 

        if (payload.msg == null) {
            log.warn "does not contain msg"
            return
        } 

        // TODO: send to relevant child device

        def msg = payload.msg

        def error = msg.error
        if (error != null) {
            log.warn "An error occurred - $error"
            return
        } 

        if (msg.cmd == null) {
            log.warn "does not contain cmd"
            return
        } 

        if (msg.cmd.equals("getDevices")) {
            if (msg.data == null) {
                log.info "Received empty device list"
                // TODO: should we remvove child device as well?
                state.devices = new ArrayList()
            } else {
                log.info "Received device list: ${msg.data}"
                // TODO:  ensure it's a list
                def devices = new ArrayList(msg.data)
                state.devices = devices
                log.info "set state.devices to $devices"
                for (deviceId in devices) {
                    if (findChildDevice(deviceId) == null) {
                        createChildDevice(deviceId)
                    }
                }
            }
            return
        }

        if (!msg.cmd.equals("devStatus")) {
            log.warn "does not contain devStatus"
            return
        }

        if (msg.data == null) {
            log.warn "does not contain data"
            return
        }

        logDebug("deviceId = ${msg.deviceId}")

        def deviceId = msg.deviceId
        if (deviceId == null) {
            log.warn "does not contain deviceId"
            return
        }

        def childDevice = findChildDevice(deviceId)
        if (childDevice == null) {
            log.warn "Device $deviceId could not be found"
            return
        }

        logDebug("data = ${msg.data}")
        def onState = msg.data.onOff
        if (onState != null) {
            if (onState == 1) {
                childDevice.sendEvent(name: "switch", value: "on")
            } else {
                childDevice.sendEvent(name: "switch", value: "off")
            }
        }

        def brightnessLevel = msg.data.level
        if (brightnessLevel != null) {
            // TODO: validate values
            childDevice.sendEvent(name: "level", value: brightnessLevel)
        }

        def color = msg.data.color
        if (color != null) {
            // TODO: validate values
            def hsv = hubitat.helper.ColorUtils.rgbToHSV([color.r, color.g, color.b])
            logDebug("setting hsv to $hsv")
            childDevice.sendEvent(name: "hue", value: hsv[0])
            childDevice.sendEvent(name: "saturation", value: hsv[1])
        }

        def colorTemp = msg.data.colorTemInKelvin
        if (colorTemp != null) {
            logDebug("setting color temp to $colorTemp")
            // TODO: validate values
            childDevice.sendEvent(name: "colorTemperature", value: colorTemp)
        }
    }  catch(e) {
        log.error("Failed to parse json e = ${e}")
        return
    }
}

def refresh() {
    unschedule(refresh)
    log.info("refresh: Refreshing device list")
    sendMsg('{"msg":{"cmd":"getDevices"}}')
    log.info("refresh: refreshing again in ${devicesPollInterval.toLong()}")
    runIn(devicesPollInterval.toLong(), "refresh")
}

def turnOn(String deviceId) {
    sendMsg('{"msg":{"cmd":"onOff", "data":1, "deviceId": "'+ deviceId + '"}}')
}

def turnOff(String deviceId) {
    sendMsg('{"msg":{"cmd":"onOff", "data":0, "deviceId": "'+ deviceId + '"}}')
}

// level required (NUMBER) - Level to set (0 to 100)
def setLevel(String deviceId, Number level) {
    sendMsg('{"msg":{"cmd":"level", "data":' + level + ', "deviceId": "'+ deviceId + '"}}')
}

// colortemperature required (NUMBER) - Color temperature in degrees Kelvin (1-30,000)
def setColorTemperature(String deviceId, Number temperature) {
    setColorTemperature(deviceId, temperature, device.currentValue("level"))
}

// colortemperature required (NUMBER) - Color temperature in degrees Kelvin (1-30,000)
// level optional (NUMBER) - level to set
def setColorTemperature(String deviceId, Number temperature, Number level) {
    log.info "Setting color temperature=$temperature, level=$level"
    sendMsg('{"msg":{"cmd":"colorTemp", "data":{"level":' + level + ', "colorTemInKelvin":' + temperature + '}, "deviceId": "'+ deviceId + '"}}')
}

// temperature required (NUMBER) - Color temperature in degrees Kelvin (1-30,000)
// level optional (NUMBER) - level to set
// transitionTime optional (NUMBER) - transition time to use in seconds (NOTE: This value is ignored)
def setColorTemperature(String deviceId, Number temperature, Number level, Number transitionTime) {
    setColorTemperature(deviceId, temperature, level)
}

// - Color map settings [hue*:(0 to 100), saturation*:(0 to 100), level:(0 to 100)]
def setColor(String deviceId, Map colormap) {
    log.info "Setting color to $colormap"
    def hsv = [colormap.hue, colormap.saturation, colormap.level]
    def rgbColor = hubitat.helper.ColorUtils.hsvToRGB(hsv)
    logDebug("Setting color: hsv=$hsv, rgb=$rgbColor")
    sendMsg('{"msg":{"cmd":"color", "data":{"r":' + rgbColor[0] + ', "g":' + rgbColor[1] + ', "b":' + rgbColor[2] + '}, "deviceId": "'+ deviceId + '"}}')
}

// hue required (NUMBER) - Color Hue (0 to 100)
def setHue(String deviceId, Number hue) {
    setColor(deviceid, ["hue": hue, "saturation": device.currentValue("saturation"), "level": device.currentValue("level")])
}

// saturation required (NUMBER) - Color Saturation (0 to 100)
def setSaturation(String deviceId, Number saturation) {
    setColor(deviceid, ["hue": device.currentValue("hue"), "saturation": saturation, "level": device.currentValue("level")])
}

def getDeviceStatus(String deviceId) {
    logDebug("Getting device status for $deviceId")
    sendMsg('{"msg":{"cmd":"devStatus", "data":{}, "deviceId": "' + deviceId + '"}}')
}

private def sendMsg(String message) {
    logDebug("sendMsg: $message")
    try {
        interfaces.webSocket.sendMessage(message)
    } catch (e) {
        log.warn "Failed to send $message"
    }
}

private def closeEventSocket()
{
    setWasExpectedClose(true)
    pauseExecution(500) // wait for state to catch up
    disconnect()
}

private def setWasExpectedClose(boolean wasExpected)
{
    logDebug("setWasExpectedClose: $wasExpected")
    state.wasExpectedClose = wasExpected
}

// private def getWasExpectedClose() {
//     synchronized(this) {
//         return state.wasExpectedClose
//     }
// }

private def createChildDevice(String deviceId) {
    log.info "Creating Govee color light device for $deviceId"
    return addChildDevice("bsoto", "Govee Color Light", deviceId, [label:"$deviceId", isComponent:false, name: "$deviceId"])
}

private def findChildDevice(String deviceId) {
    return getChildDevice(deviceId)
}

private def manageChildDevice(String id) {
    logDebug("manageChildDevice($id)")
    
    def child = findChildDevice(id)
    if (child) {
        logDebug("found existing child: ${child}")
    } else {
        child = createChildDevice(name, id)
        logDebug("created new child: ${child}")
    }
    return child
}