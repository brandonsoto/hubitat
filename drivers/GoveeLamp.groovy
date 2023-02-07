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
    definition (name: "Govee Color Light", namespace: "bsoto", author: "Brandon Soto", importUrl: "") {
        capability "ColorControl"
        capability "ColorTemperature"
        capability "Refresh"
        capability "Switch"
        capability "SwitchLevel"
    }

    preferences {
        input name: "deviceAddress", type: "text", title: "Device IP Address", description: "The device's IP address", required: true
        input name: "devicePort", type: "number", title: "Device Port", description: "The device's port for receiving commands", required: true, defaultValue: 4003
        input name: "serverAddress", type: "text", title: "Server IP Address", description: "The server's IP address", required: true, defaultValue: "239.255.255.250"
        input name: "serverPort", type: "number", title: "Server Port", description: "The server's port for receiving responses from the device", required: true, defaultValue: 4002
        // input name: "pollIntervals", type: "enum", title: "Set the Poll Interval.", options: [0:"off", 60:"1 minute", 120:"2 minutes", 180:"3 minutes", 300:"5 minutes",600:"10 minutes",900:"15 minutes",1800:"30 minutes",3600:"60 minutes"], required: true, defaultValue: "600"
        input name: "debugOutput", type: "bool", title: "Enable debug logging?", defaultValue: false
    }
}

def initialize() {
    log.info "Initialize"
    // poll()
}

def updated() {
    log.info "Called updated"
    initialize()
}

def parse(String description) {
    log.debug "parse: description=$description"
}

def poll() {
    // pollInterval = pollIntervals.toInteger()
    // if (pollInterval) runIn(pollInterval, poll) 
    // logInfo "in poll: (every $pollInterval seconds)"
    // refresh()
}

def refresh() {
    log.info "Refreshing device status"
    // TODO: check dev status
}

def sendUDPCommand(String rawCommand) {
    def address = "$settings.deviceAddress:$settings.devicePort"
    def command = new hubitat.device.HubAction(
        rawCommand,
        hubitat.device.Protocol.LAN,
        [
            destinationAddress: address,
            type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT
        ]
    )
    // TODO: debug log the action
    log.info "Sending $command to $address"
    sendHubCommand(command)
}

// colortemperature required (NUMBER) - Color temperature in degrees Kelvin (1-30,000)
def setColorTemperature(temperature) {
    log.info "Setting color temperature to temperature=$temperature"
}

// colortemperature required (NUMBER) - Color temperature in degrees Kelvin (1-30,000)
// level optional (NUMBER) - level to set
def setColorTemperature(temperature, level) {
    log.info "Setting color temperature to temperature=$temperature, level=$level"
    // if (level >= 0 && level <= 100) { }
}

// colortemperature required (NUMBER) - Color temperature in degrees Kelvin (1-30,000)
// level optional (NUMBER) - level to set
// transitionTime optional (NUMBER) - transition time to use in seconds
def setColorTemperature(temperature, level, transitionTime) {
    log.info "Setting color temperature to temperature=$temperature, level=$level, transitionTime=$transitionTime"
    // if (level >= 0 && level <= 100) { }
    // if (transitionTime >= 0) { }
}

// - Color map settings [hue*:(0 to 100), saturation*:(0 to 100), level:(0 to 100)]
def setColor(colormap) {
    log.info "Setting color to $colormap"
}

// hue required (NUMBER) - Color Hue (0 to 100)
def setHue(hue) {
    log.info "Setting hue to $hue"
    // if (hue >= 0 && hue <= 100) { }
}

// saturation required (NUMBER) - Color Saturation (0 to 100)
def setSaturation(saturation) {
    log.info "Setting saturation to $saturation"
    // if (saturation >= 0 && saturation <= 100) { }
}

def on() {
    log.info "Turning light on"
    sendUDPCommand('{"msg":{"cmd":"turn","data":{"value":1}}}')
    sendEvent(name: "switch", value: "on")
}

def off() {
    log.info "Turning light off"
    sendUDPCommand('{"msg":{"cmd":"turn","data":{"value":0}}}')
    sendEvent(name: "switch", value: "off")
}

// level required (NUMBER) - Level to set (0 to 100)
def setLevel(level) {
    log.info "Setting level to $level"
}

// level required (NUMBER) - Level to set (0 to 100)
// duration optional (NUMBER) - Transition duration in seconds
def setLevel(level, duration) {
    log.info "Setting level to $level (duration=$duration)"
    // if (level >= 0 && level <= 100) { }
    // if (duration >= 0) { }
}