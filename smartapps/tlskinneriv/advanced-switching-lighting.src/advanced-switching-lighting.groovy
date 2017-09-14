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
	page(name: "page1", nextPage: "page2", title: "What and When", install: false, uninstall: true)
    page(name: "page2", nextPage: "pageLast", title: "Schedule Exceptions", uninstall: false)
    page(name: "pageLast", title: "Advanced Options", install: true, uninstall: false) {
    	section([mobileOnly:true]) {
            label title: "Assign a name", required: false
    	}
	}
    
}

// devices and options dynamic page
def page1() {
	dynamicPage(name: "page1") {
    	section {
        	// define device capability and type
            def capability = "switch"
            state.deviceType = "switch"
            
            // get the devices
        	input (name: "theDevices", type: "capability." + capability, multiple:true, title: "Which devices do you want to control?",
            	required: true, submitOnChange: true)
            
            // get the actions and triggers
            if ( theDevices )
            {
                // get the regular action
                input (name: "theDevices_actionRegular", type: "enum", title: "What do you want to do?", options: deviceActions(state.deviceType),
                    defaultValue: deviceActionsDefault(state.deviceType), required: true, submitOnChange: true)
                
                // store the regular action so we can reference it in other pages
                state.theDevices_actionRegular = theDevices_actionRegular
                // store the opposite action so we can reference it in other pages
                state.theDevices_actionOpposite = deviceActionsOpposite(state.deviceType, theDevices_actionRegular)
                
                // get the regular trigger
                input (name: "theDevices_triggerRegular", type: "enum", title: "Select trigger", options: triggerOptions(),
                	required: true, submitOnChange: true)
                
                // get the regular trigger options
                if ( theDevices_triggerRegular ) {
                	// render the trigger options after selected
                    renderTriggerOptions(theDevices_triggerRegular, "regular")
                    
                    // offer another action that is not the selected trigger only if one other option
                    def userString = "Also '" + state.theDevices_actionOpposite + "' with a different trigger?"
                    input (name: "theDevices_oppositeEnable", type: "bool", title: userString, required: false, submitOnChange: true)
                    
                    // get the opposite trigger
                    if ( theDevices_oppositeEnable ) {
                        def oppositeTriggers = triggerOptionsOpposite(theDevices_triggerRegular)
                        input (name: "theDevices_triggerOpposite", type: "enum", title: "Select trigger", options: oppositeTriggers,
                               required: true, submitOnChange: true)
                        if ( theDevices_triggerOpposite ) {
                            // render the trigger options after selected
                            renderTriggerOptions(theDevices_triggerOpposite, "opposite")
                        }
                    }
                }
			}
		}
    }
}

// schedule exceptions dynamic page
def page2() {
	dynamicPage(name: "page2") {
        section {
            // when in what mode
            paragraph "When home enters 'pause' mode(s) the app will perform the action '" + state.theDevices_actionOpposite + 
            	"'. When home enters 'resume' mode(s) the app will perform the action '" + state.theDevices_actionRegular + "' if it was previously scheduled to do so." 
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
	//remove any previously scheduled tasks
    unschedule()
	unsubscribe()
	initialize()
}

def initialize() {
    subscribe(theDevices, state.deviceType, deviceChangeHandler)
    subscribe(location, "sunsetTime", sunsetTimeHandler)
    subscribe(location, "sunriseTime", sunriseTimeHandler)
    if ( modeSelect_resume && modeSelect_pause ) subscribe(location, "mode", modeChangeHandler)
    
    state.deviceTriggeredRegular = false
	state.deviceSchedulePaused = false
    
    processTrigger(theDevices_triggerRegular, "regular")
    if ( theDevices_oppositeEnable ) processTrigger(theDevices_triggerOpposite, "opposite")
}

/********** Page Rendering **********/

// gets device options for specific device types
private deviceActions(deviceType) {
	switch(deviceType) {
    	case "switch":
        	return ["Turn On","Turn Off"]
    	default:
        	return ["UNDEFINED"]
    }
}

// gets device options that are not the options provided
private deviceActionsOpposite(deviceType, option) {
	def allOptions = deviceActions(deviceType)
    allOptions = allOptions.findAll { it != option }
    return allOptions[0]
}

// gets default device option
private deviceActionsDefault(deviceType) {
	switch(deviceType) {
    	case "switch":
        	return "Turn On"
    	default:
        	return null
    }
}

// lists available triggers
private triggerOptions() {
	def triggers = [
    	"At Sunset",
        "At Sunrise",
        "At a Specific Time"
    ]
    return triggers.sort()
}

// lists trigger options that are not the trigger provided
private triggerOptionsOpposite(trigger) {
	// get all options
    def allOptions = triggerOptions()
    // list options that cannot be duplicated on a device
    def nonDuplicateTriggers = [
    	"At Sunset",
        "At Sunrise"
    ]
    // list options that can only be used after the initial trigger
    def postTriggers = [
    	"After an Amount of Time"
    ]
    // process inputted trigger
    if (nonDuplicateTriggers.contains(trigger)) allOptions = allOptions.findAll { it != trigger }
    allOptions = allOptions + postTriggers
    allOptions = allOptions.sort()
    return allOptions
}

// renders trigger options for specific triggers
private renderTriggerOptions(trigger, nameSuffix) {
	def triggerSuffix = (nameSuffix.toLowerCase()).capitalize()
    def triggerPrefix = "theDevices_trigger" + triggerSuffix + "_"
    switch (trigger) {
    	case "At Sunrise":
        	// show sunset options
            input (name: triggerPrefix + "offset", type: "number", title: "Sunrise offset +/- minutes", required: false)
            break
        case "At Sunset":
        	// show sunrise options
            input (name: triggerPrefix + "offset", type: "number", title: "Sunset offset +/- minutes", required: false)
            break
        case "At a Specific Time":
        	// show specific time options
            input (name: triggerPrefix + "time", type: "time", title: "Time", required: true)
            break
        case "After an Amount of Time":
        	// show after an amount of time options
            paragraph "The minimum amount of time for this offset is 5 minutes regardless of what values are input below."
            input (name: triggerPrefix + "hours", type: "number", title: "Hours", required: false)
            input (name: triggerPrefix + "mins", type: "number", title: "Minutes", required: false)
            break
        default:
        	paragraph "This trigger's options are not currently defined."
    }
}

/********** Process Triggers **********/

def processTriggerRegular(triggerRegular) {
	def triggerPrefix = "theDevices_triggerRegular_"
    log.debug "Initializing regular trigger '$triggerRegular'"
    switch (triggerRegular) {
    	case "At Sunset":
        	// do sunset init
            log.debug "init trigger option ${triggerPrefix}offset is ${settings."${triggerPrefix}offset"}"
            scheduleDeviceAction(location.currentValue("sunsetTime"), false, false, settings."${triggerPrefix}offset")
            break
        case "At Sunrise":
        	// do sunrise init
            log.debug "init trigger option ${triggerPrefix}offset is ${settings."${triggerPrefix}offset"}"
            scheduleDeviceAction(location.currentValue("sunriseTime"), false, false, settings."${triggerPrefix}offset")
            break
        case "At a Specific Time":
        	// do specific time init
            log.debug "init trigger option ${triggerPrefix}time is ${settings."${triggerPrefix}time"}"
            scheduleDeviceAction(settings."${triggerPrefix}time", true, false)
            break
        default:
        	log.debug "No definition for trigger ${triggerRegular}"
    }
}

def processTrigger(trigger, nameSuffix) {
	def triggerSuffix = (nameSuffix.toLowerCase()).capitalize()
    def triggerPrefix = "theDevices_trigger" + triggerSuffix + "_"
    log.debug "Initializing $nameSuffix trigger '$trigger'"
    switch (trigger) {
    	case "At Sunset":
        	// do sunset init
            log.debug "---- trigger option ${triggerPrefix}offset is ${settings."${triggerPrefix}offset"}"
        	// schedule once at sunset
            scheduleDeviceAction(location.currentValue("sunsetTime"), false, (nameSuffix == "opposite"), settings."${triggerPrefix}offset")
            // store scheduled time in state
            break
        case "At Sunrise":
        	// do sunrise init
            log.debug "---- trigger option ${triggerPrefix}offset is ${settings."${triggerPrefix}offset"}" 
			// schedule once at sunrise
            scheduleDeviceAction(location.currentValue("sunriseTime"), false, (nameSuffix == "opposite"), settings."${triggerPrefix}offset")
            break
        case "At a Specific Time":
        	// do specific time init
            log.debug "---- trigger option ${triggerPrefix}time is ${settings."${triggerPrefix}time"}"
            // schedule daily at this time
            scheduleDeviceAction(settings."${triggerPrefix}time", true, (nameSuffix == "opposite"))
            break
        case "After an Amount of Time":
        	// POST ACTION ONLY
            // do after amount of time init
            log.debug "---- trigger option ${triggerPrefix}hours is ${settings."${triggerPrefix}hours"}"
            log.debug "---- trigger option ${triggerPrefix}mins is ${settings."${triggerPrefix}mins"}"
            // schedule once at regular scheduled time + offset defined in settings
            def offset = getOffsetFromTrigger ( settings."${triggerPrefix}hours", settings."${triggerPrefix}mins" )
            scheduleDeviceAction(state.regularActionScheduledTime, false, (nameSuffix == "opposite"), offset)
            break
        default:
        	log.debug "No definition for trigger ${trigger}"
    }
}

private getOffsetFromTrigger ( triggerOffsetHours, triggerOffsetMins, minimumEnabled = true ) {
	def hours = (triggerOffsetHours && triggerOffsetHours >= 0 ) ? triggerOffsetHours : 0
    def minutes = (triggerOffsetMins && triggerOffsetMins >= 0 ) ? triggerOffsetMins : 0
    def offset = (hours) * 60 + minutes
    offset = (offset < 5 && minimumEnabled) ? 5 : offset // minimum 5 minute offset
    return offset
}

/********** Event Handlers **********/

def deviceChangeHandler(evt) {
	log.debug "Switch ${evt.displayName} turned ${evt.stringValue}."
}

def modeChangeHandler(evt) {
	log.debug "Mode changed to ${location.mode}."
    // if in the mode we want to ensure regular action happens
    if (settings.modeSelect_resume.contains(location.mode)) {
    	// only make it happen if it was already supposed to
        log.debug "Resuming the schedule..."
        state.deviceSchedulePaused = false
        if ( state.deviceTriggeredRegular ) doDeviceAction_regular()
    }
    else if (settings.modeSelect_pause.contains(location.mode)) {
        log.debug "Pausing the schedule..."
        doDeviceAction_opposite()
        state.deviceSchedulePaused = true
    }
}

def sunsetTimeHandler(evt) {
    // check both triggers and set as needed
    if ( theDevices_triggerRegular == "At Sunset" ) {
    	def triggerPrefix = "theDevices_triggerRegular_"
        log.debug "Resetting regular trigger at sunrise"
        log.debug "---- trigger option ${triggerPrefix}offset is ${settings."${triggerPrefix}offset"}"
    	// schedule once at sunset
    	scheduleDeviceAction(evt.value, false, false, settings."${triggerPrefix}offset")
        resetAmountOfTimeTrigger()
    }
    if ( theDevices_triggerOpposite == "At Sunset" ) {
    	def triggerPrefix = "theDevices_triggerOpposite_"
        log.debug "Resetting opposite trigger at sunrise"
        log.debug "---- trigger option ${triggerPrefix}offset is ${settings."${triggerPrefix}offset"}"
    	// schedule once at sunset
    	scheduleDeviceAction(evt.value, false, true, settings."${triggerPrefix}offset")
    }
}

def sunriseTimeHandler(evt) {
	// check both triggers and set as needed
    if ( theDevices_triggerRegular == "At Sunrise" ) {
    	def triggerPrefix = "theDevices_triggerRegular_"
        log.debug "Resetting regular trigger at sunrise"
        log.debug "---- trigger option ${triggerPrefix}offset is ${settings."${triggerPrefix}offset"}"
    	// schedule once at sunrise
    	scheduleDeviceAction(evt.value, false, false, settings."${triggerPrefix}offset")
        resetAmountOfTimeTrigger()
    }
    if ( theDevices_triggerOpposite == "At Sunrise" ) {
    	def triggerPrefix = "theDevices_triggerOpposite_"
        log.debug "Resetting opposite trigger at sunrise"
        log.debug "---- trigger option ${triggerPrefix}offset is ${settings."${triggerPrefix}offset"}"
    	// schedule once at sunrise
    	scheduleDeviceAction(evt.value, false, true, settings."${triggerPrefix}offset")
    }
}

def resetAmountOfTimeTrigger () {
	if ( theDevices_triggerOpposite == "After an Amount of Time" )
    {
        def triggerPrefix = "theDevices_triggerOpposite_"
        log.debug "Resetting opposite trigger after an amount of time"
        log.debug "---- trigger option ${triggerPrefix}hours is ${settings."${triggerPrefix}hours"}"
        log.debug "---- trigger option ${triggerPrefix}mins is ${settings."${triggerPrefix}mins"}"
        // schedule once at regular scheduled time + offset defined in settings
        def offset = getOffsetFromTrigger ( settings."${triggerPrefix}hours", settings."${triggerPrefix}mins" )
        scheduleDeviceAction(state.regularActionScheduledTime, false, true, offset)
    }
}

/********** Device Action Handlers **********/
// performs the regular action on the devices
def doDeviceAction_regular() {
	// do the regular action
    log.debug "Doing the regular action on devices"
    if ( !state.deviceSchedulePaused ) 
    	switch (state.deviceType) {
        	case "switch": 
            	performSwitchAction(state.theDevices_actionRegular)
                break
            default: log.debug "No action defined for device type"
        }
}

// performs the opposite action on the devices
def doDeviceAction_opposite() {
	// do the opposite action
    log.debug "Doing opposite action on the devices"
    switch (state.deviceType) {
        case "switch": 
        	performSwitchAction(state.theDevices_actionOpposite)
        	break
        default: log.debug "No action defined for device type"
    }
}

// performs regular action on devices and marks that it is scheduled in regular mode by a trigger
def doScheduledDeviceAction_regular() {
	state.deviceTriggeredRegular = true
    log.debug "Setting state.deviceTriggeredRegular to ${state.deviceTriggeredRegular}"
    log.debug "Doing scheduled regular action..."
    doDeviceAction_regular()
}

// performs regular action on devices and marks that it is not scheduled in regular mode by a trigger
def doScheduledDeviceAction_opposite() {
	state.deviceTriggeredRegular = false
    log.debug "Setting state.deviceTriggeredRegular to ${state.deviceTriggeredRegular}"
    log.debug "Doing scheduled opposite action..."
    doDeviceAction_opposite()
}

/********** Device Action Performers **********/
// performs string actions for devices of type: switch
def performSwitchAction(action) {
	log.debug "action is $action"
    if ( action == "Turn On" ) theDevices.on()
    else theDevices.off()
}

/********** Schedulers **********/

def scheduleDeviceAction(timeInput, daily = false, opposite = false, offset = 0) {
	// ensure there is an offset
    if ( !offset ) { offset = 0 }
    // parse time from string and add the offset
    def time = null
    try { time = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", timeInput) } catch (all1) {
    	try { time = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timeInput) } catch (all2) { 
        		time = timeInput
            }
    	}	
    if ( time )
    {
        def timeWithOffset = new Date(time.time + (offset * 60 * 1000))
        // schedule the appropriate action
        if ( opposite ) { 
        	if ( daily ) {
            	schedule(timeWithOffset, doScheduledDeviceAction_opposite)
                log.debug "Scheduling opposite device action for: ${time.format("HH:mm:ss z")} every day"
            }
            else {
            	runOnce(timeWithOffset, doScheduledDeviceAction_opposite)
            	log.debug "Scheduling opposite device action for: $timeWithOffset"
            }
        }
        else { 
        	// store the time for the regular action
            state.regularActionScheduledTime = timeWithOffset
        	if ( daily ) {
            	schedule(timeWithOffset, doScheduledDeviceAction_regular)
                log.debug "Scheduling regular device action for: ${time.format("HH:mm:ss z")} every day"
            }
            else {
            	runOnce(timeWithOffset, doScheduledDeviceAction_regular)
            	log.debug "Scheduling regular device action for: $timeWithOffset" 
            } 
        }
    } else { log.debug "Could not parse time. Action not scheduled." }
}