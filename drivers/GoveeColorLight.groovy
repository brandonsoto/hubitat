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
        input name: "pollInterval", type: "number", title: "Set the Poll Interval (seconds)", defaultValue: 300
        input name: "debugOutput", type: "bool", title: "Enable debug logging?", defaultValue: false
    }
}

def logDebug(String message) {
    if (debugOutput) {
        log.debug(message)
    }
}

def initialize() {
    logDebug("initialize")
    refresh()
}

def updated() {
    logDebug("updated")
    initialize()
}

def uninstalled() {
    logDebug("uninstalled")
    unschedule()
}

def poll() {
    logDebug("poll: +")
    logDebug("poll: (every $pollInterval seconds)")
    unschedule(poll)
    refresh()

    runIn(interval, "poll") 
    logDebug("poll: -")
}

def refresh() {
    log.info "Refreshing device status"
    unschedule("refresh")
    parent.getDeviceStatus(device.deviceNetworkId)
    def interval = pollInterval.toInteger()
    logDebug("refresh again in $interval seconds")
    runIn(interval, "refresh") 
}

// colortemperature required (NUMBER) - Color temperature in degrees Kelvin (1-30,000)
def setColorTemperature(Number temperature) {
    setColorTemperature(temperature, device.currentValue("level"), 0)
}

// colortemperature required (NUMBER) - Color temperature in degrees Kelvin (1-30,000)
// level optional (NUMBER) - level to set
def setColorTemperature(Number temperature, Number level) {
    setColorTemperature(temperature, level, 0)
}

// colortemperature required (NUMBER) - Color temperature in degrees Kelvin (1-30,000)
// level optional (NUMBER) - level to set
// transitionTime optional (NUMBER) - transition time to use in seconds
def setColorTemperature(Number temperature, Number level, Number transitionTime) {
    log.info "Setting color temperature to temperature=$temperature, level=$level, transitionTime=$transitionTime"
    sendEvent(name: "level", value: level)
    sendEvent(name: "colorTemperature", value: temperature)
    parent.setColorTemperature(device.deviceNetworkId, temperature, level)
}

// - Color map settings [hue*:(0 to 100), saturation*:(0 to 100), level:(0 to 100)]
def setColor(Map colormap) {
    log.info "Setting color to $colormap"
    parent.setColor(device.deviceNetworkId, colormap)
}

// hue required (NUMBER) - Color Hue (0 to 100)
def setHue(Number hue) {
    log.info "Setting hue to $hue"
    sendEvent(name: "hue", value: hue)
    parent.setHue(device.deviceNetworkId, hue)
}

// saturation required (NUMBER) - Color Saturation (0 to 100)
def setSaturation(Number saturation) {
    log.info "Setting saturation to $saturation"
    sendEvent(name: "saturation", value: hue)
    parent.setSaturation(device.deviceNetworkId, saturation)
}

def on() {
    log.info "Turning light on"
    sendEvent(name: "switch", value: "on")
    parent.turnOn(device.deviceNetworkId)
}

def off() {
    log.info "Turning light off"
    sendEvent(name: "switch", value: "off")
    parent.turnOff(device.deviceNetworkId)
}

// level required (NUMBER) - Level to set (0 to 100)
def setLevel(Number level) {
    log.info "Setting level to $level"
    sendEvent(name: "level", value: level)
    parent.setLevel(device.deviceNetworkId, level)
}