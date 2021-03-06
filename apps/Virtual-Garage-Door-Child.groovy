/**
 *  Virtual Garage Door Manager
 *
 *  Copyright 2017 Patrick McKinnon
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Author: Patrick McKinnon (patrick@ojolabs.com)
 *  Hubitat patches from Brandon Soto (brandon.soto09@gmail.com)
 */

definition(
    name: "Virtual Garage Door",
    namespace: "peng1can",
    author: "peng1can@gmail.com",
    description: "Manages state of 'Virtual Garage Door' device (Child App)",
    category: "Convenience",
    parent: "peng1can/parent:Virtual Garage Door Manager",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/garage_contact.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/garage_contact@2x.png"
)

preferences {
    section("Garage door actuator"){
        input "actuatorSwitch", "capability.switch",
		title: "Garage Door Actuator Switch",
            required: true
	}
    section("Garage door actuator off delay"){
        input "actuatorSwitchDelay", "number", title: "Turns off the switch after X seconds", defaultValue: 2, required: true
	}
	
    section("Garage door multisensor"){
        input "contactSensor", "capability.contactSensor",
            title: "Garage Door Contact Sensor",
            required: true
        input "accelerationSensor", "capability.accelerationSensor",
            title: "Garage Door Acceleration Sensor",
            required: true
	}

    section("Virtual garage door device"){
        input "garage", "capability.doorControl",
            title: "Virtual Garage Door",
            required: true
	}
}

def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    initialize()
}

private initialize() {
    if(garage.supportedCommands.find { it.name == "setVirtualGarageState" }) {
        subscribe(contactSensor, "contact", contactHandler)
        subscribe(accelerationSensor, "acceleration", accelerationHandler)
        subscribe(garage, "door", garageControlHandler)
        subscribe(garage, "switch", garageSwitchHandler)
        subscribe(actuatorSwitch, "switch", actuatorHandler)


        state.direction = null
        state.current = contactSensor.currentContact

        synchronize()
    }
    else {
        log.error("Virtual Garage Door device should be of type 'Virtual Garage Door'")
    }
}

private synchronize() {
    log.debug "synchronize, current: $state.current, direction: $state.direction"

    garage.setVirtualGarageState([
        door: state.direction ? state.direction : state.current
    ])
}

def accelerationHandler(evt) {
    log.debug "accelerationHandler($evt.value)"
    onSensorChanged()
}

def contactHandler(evt) {
    log.debug "contactHandler($evt.value)"
    onSensorChanged()
}

private onSensorChanged() {
    log.debug("onSensorChanged($contactSensor.currentContact, $accelerationSensor.currentAcceleration), current: $state.current, direction: $state.direction")
    if(accelerationSensor.currentAcceleration == "inactive")  {
        // Motion has stabilized, save the current state
        state.direction = null
        state.current = contactSensor.currentContact
    }
    else if(!state.direction) {
        // Door is moving because of outside event (button, opener, etc)
        //  Guess the direction based on the previously stable state
        state.direction = (state.current == "open") ? "closing" : "opening"
    }

    synchronize()
}

def garageControlHandler(evt) {
    log.debug "garageControlHandler($evt.value)"
}

def garageSwitchHandler(evt) {
    log.debug "garageSwitchHandler($evt.value), current: $state.current"

    // Only take action if our direction state has stabilized
    if(state.direction == null) {
        if(evt.value == "on") {
            if(state.current != "open") {
                state.direction = "opening"
                triggerActuator()
            }
        }
        else {
            if(state.current != "closed") {
                state.direction = "closing"
                triggerActuator()
            }
        }
    }

    synchronize()
}

def actuatorHandler(evt) {
    log.debug "actuatorHandler($evt), current_state: $state.current"
}

private triggerActuator() {
    log.debug "triggerActuator()"
    actuatorSwitch.on()
    runIn(actuatorSwitchDelay, 'actuatorDelayOff')
}

private actuatorDelayOff() {
    log.debug "actuatorDelayOff"
    actuatorSwitch.off()
}
