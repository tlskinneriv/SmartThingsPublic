/**
 *  Advanced Switching/Lighting
 *
 *  Copyright 2017 Thomas Skinner
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
 */
definition(
    name: "Advanced Switching/Lighting",
    namespace: "tlskinneriv",
    author: "Thomas Skinner",
    description: "Advanced switching scheduler with triggers and schedule exceptions",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")

preferences {
	page(name: "page1", nextPage: "page2", title: "What to control...", install: false, uninstall: true)
    page(name: "page2", title: "Schedule Exceptions", install: true, uninstall: true)
}

def page1() {
	dynamicPage(name: "page1") {
    	section {
        	// show the input for switches always
        	input (name: "theSwitches", type: "capability.switch", multiple:true, title: "Which switches/lights do you want to control?",
            	required: true)
			input (name: "switchesAction", type: "enum", title: "What do you want to do?", options: ["Turn On","Turn Off"],
				required: true, submitOnChange: true)
            if (switchesAction) {
            	// find out what is opposite for switch
                def switchesAction_opposite = (switchesAction == "Turn On") ? "Turn Off" : "Turn On"
                state.switchesAction = switchesAction
                state.switchesAction_opposite = switchesAction_opposite
                // get the solar data
                input (name: "solarSelect", type: "enum", title: "At sunset/sunrise?", multiple: false, options: ["Sunset", "Sunrise"],
                	required: true, submitOnChange: true)
                if (solarSelect) input (name: "solarSelect_offset", type: "number", title: solarSelect + " offset +/- minutes", required: false)
                
                // if action at sunrise/sunset and back off at opposite
                if (solarSelect && !solarSelect_specificTime && !solarSelect_amountTime) {
                	
                    def solarSelect_opposite_mode = (solarSelect == "Sunset") ? "sunrise" : "sunset"
                    input (name: "solarSelect_opposite", type: "bool", title: "Also '" + switchesAction_opposite + "' at " + solarSelect_opposite_mode + "?",
                    	required: false, submitOnChange: true)
                    if (solarSelect_opposite)
                    {
                    	input (name: "solarSelect_opposite_offset", type: "number", title: solarSelect_opposite_mode.capitalize() + " offset +/- minutes", required: false)
					}
            	}
                
                // if action at sunrise/sunset and back off after amount of time
                if (solarSelect && !solarSelect_specificTime && !solarSelect_opposite) {
                    input (name: "solarSelect_amountTime", type: "bool", title: "Also '" + switchesAction_opposite + "' after an amount of time?",
                    	required: false, submitOnChange: true)
                    if (solarSelect_amountTime)
                    {
                    	paragraph "The minimum amount of time for this offset is 5 minutes regardless of what values are input below"
                        input (name: "solarSelect_amountTime_hours", type: "number", title: "Hours", required: false)
                        input (name: "solarSelect_amountTime_minutes", type: "number", title: "Minutes", required: false)
					}
            	}
                
                // if action at sunrise/sunset and back off at specific time
                if (solarSelect && !solarSelect_opposite && !solarSelect_amountTime) {
                    input (name: "solarSelect_specificTime", type: "bool", title: "Also '" + switchesAction_opposite + "' at a specific time?",
                    	required: false, submitOnChange: true)
                    if (solarSelect_specificTime)
                    {
                    	input (name: "solarSelect_specificTime_time", type: "time", title: "Time", required: true)
					}
            	}
        	}
		}
    }
}

def page2() {
	dynamicPage(name: "page2") {
        section {
            // when in what mode
            paragraph "When home enters 'pause' mode(s) the app will perform the action '" + state.switchesAction_opposite + 
            	"'. When home enters 'pause' mode(s) the app will perform the action '" + state.switchesAction + "' if it was previously scheduled to do so." 
            if ( modeSelect_resume ) input (name: "modeSelect_pause", type: "mode", title: "Pause schedule in these modes", multiple: true, required: true, submitOnChange: true)
            else input (name: "modeSelect_pause", type: "mode", title: "Pause schedule in these modes", multiple: true, required: false, submitOnChange: true)
            if ( modeSelect_pause ) input (name: "modeSelect_resume", type: "mode", title: "Resume schedule in these modes", multiple: true, required: true, submitOnChange: true)
            else input (name: "modeSelect_resume", type: "mode", title: "Resume schedule in these modes", multiple: true, required: false, submitOnChange: true)
        }
    }
}

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize() {
	// TODO: subscribe to attributes, devices, locations, etc.
    subscribe(theSwitches, "switch", switchHandler)
	subscribe(location, "mode", modeChangeHandler)
    subscribe(location, "sunsetTime", sunsetTimeHandler)
    subscribe(location, "sunriseTime", sunriseTimeHandler)
    state.switchScheduledRegular = false
	state.switchPaused = false
    // schedules switch to execute regular action at specified solar event
    scheduleSwitchAction_solar(location.currentValue(solarSelect.toLowerCase() + "Time"), false, solarSelect_offset)
    // schedules switch to execute opposite action at opposite specified solar event
	if ( solarSelect_opposite ) {
		def solarSelect_opposite_value = ( solarSelect == "Sunset" ? "Sunrise" : "Sunset" )
		scheduleSwitchAction_solar(location.currentValue(solarSelect_opposite_value.toLowerCase() + "Time"), true, solarSelect_opposite_offset)
	}
    // schedules switch to execute opposite action at at a specific time
    else if ( solarSelect_specificTime ) {
        scheduleSwitchAction_specific(solarSelect_specificTime_time, true)
    }
    // schedules switch to execute opposite action after an amount of time
    else if ( solarSelect_amountTime ) {
    	def hours = solarSelect_amountTime_hours ? solarSelect_amountTime_hours : 0
        def minutes = solarSelect_amountTime_mins ? solarSelect_amountTime_mins : 0
        state.amountTime_offset = (hours) * 60 + minutes
        state.amountTime_offset = (offset < 5) ? 5 : state.amountTime_offset // minimum 5 minute offset
    	scheduleSwitchAction_solar(location.currentValue(solarSelect.toLowerCase() + "Time"), true, state.amountTime_offset )
    }
}

// TODO: implement event handlers

def switchHandler(evt) {
	log.debug "Switch ${evt.displayName} turned ${evt.stringValue}."
}

def modeChangeHandler(evt) {
	log.debug "Mode changed to ${location.mode}."
    // if in the mode we want to ensure regular action happens
    if (settings.modeSelect_resume.contains(location.mode)) {
    	// only make it happen if it was already supposed to
        state.switchPaused = false
        log.debug "Resuming the schedule..."
        if ( state.switchScheduledRegular ) doSwitchAction_regular()
    }
    else if (settings.modeSelect_pause.contains(location.mode)) {
        doSwitchAction_opposite()
        log.debug "Pausing the schedule..."
        state.switchPaused = true
    }
}

def sunsetTimeHandler(evt) {
	// if we want regular state after sunset
    if ( solarSelect == "Sunset" ) {
    	scheduleSwitchAction_solar(evt.value)
        if ( solarSelect_amountTime ) {
    		scheduleSwitchAction_solar(evt.value, true, state.amountTime_offset )
    	}
	}
    // if we want opposite state after sunset
    else if ( solarSelect_opposite ) {
    	scheduleSwitchAction_solar(evt.value, true)
    }
}

def sunriseTimeHandler(evt) {
	// if we want regular state after sunset
    if ( solarSelect == "Sunrise" ) {
    	scheduleSwitchAction_solar(evt.value)
        if ( solarSelect_amountTime ) {
    		scheduleSwitchAction_solar(evt.value, true, state.amountTime_offset )
    	}
	}
    // if we want opposite state after sunset
    else if ( solarSelect_opposite ) {
    	scheduleSwitchAction_solar(evt.value, true)
    }
}

def doSwitchAction_regular() {
	// do the regular action
    log.debug "Doing the regular action on switches..."
    if ( !state.switchPaused) (switchesAction == "Turn On") ? theSwitches.on() : theSwitches.off()
}

def doSwitchAction_opposite() {
	// do the opposite action
    log.debug "Doing opposite action on switches..."
    (switchesAction == "Turn On") ? theSwitches.off() : theSwitches.on()
}

def doScheduledSwitchAction_regular() {
	state.switchScheduledRegular = true
    log.debug "Setting state.switchScheduledRegular to ${state.switchScheduledRegular}"
    log.debug "Doing scheduled regular action..."
    doSwitchAction_regular()
}

def doScheduledSwitchAction_opposite() {
	state.switchScheduledRegular = false
    log.debug "Setting state.switchScheduledRegular to ${state.switchScheduledRegular}"
    log.debug "Doing scheduled opposite action..."
    doSwitchAction_opposite()
}

def scheduleSwitchAction_solar(timeString, opposite = false, offset = 0) {
	// ensure there is an offset
    if ( !offset ) { offset = 0 }
    // parse time from string and add the offset
    def time = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timeString)
    def timeWithOffset = new Date(time.time + (offset * 60 * 1000))
    // schedule the appropriate action
    if ( opposite ) { runOnce(timeWithOffset, doScheduledSwitchAction_opposite); log.debug "Scheduling opposite switch action for: $timeWithOffset" }
    else { runOnce(timeWithOffset, doScheduledSwitchAction_regular); log.debug "Scheduling regular switch action for: $timeWithOffset" }
}

def scheduleSwitchAction_specific(timeString, opposite = false) {
    // schedule the appropriate action
    def time = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", timeString)
    if ( opposite ) { schedule(timeString, doScheduledSwitchAction_opposite); log.debug "Scheduling opposite switch action for: ${time.format("HH:mm:ss z")} every day" }
    else { schedule(timeString, doScheduledSwitchAction_regular); log.debug "Scheduling regular switch action for: ${time.format("HH:mm:ss z")} every day" }
}