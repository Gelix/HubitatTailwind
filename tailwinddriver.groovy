preferences {
    input name: "IP", type: "string", title: "Tailwind Controller IP", required: "True"
    input name: "cName", type: "string", title: "Tailwind Controller Name", required: "True",  description: '<em>Changes the name for the controller displayed in dashboards, DOES affect children unique deviceNetworkId.  Changing this will re-create the children devices.</em>'
    //input name: "token", type: "password", title: "Access Token", required: "True"
    input name: "doorCount", type: "number", title: "Number of Doors", required: "True", range: "0..3", defaultValue : 1
    input name: "interval", type: "enum", title: "Polling interval", required: "True", options: ["1", "5", "10", "15", "30"], defaultValue : 1, description: '<em> Minutes, if you want it more frequently, use Rules.</em>'
    input name: "garageDoorTimeout", type: "number", title: "Door Open/Close timeout", required: "True", defaultValue : 60, description: '<em> Seconds. How long should faster polling be run before giving up on waiting to check and see if the door status has changed after issuing a command.</em>'
    input name: "debugEnable", type: "bool", title: "Enable debug logging?", defaultValue: true,  description: '<em>for 2 hours</em>'
    if(doorCount > 0){input name: "d1Name", type: "string", title: "Door 1 Name", required: "false", defaultValue : "Door 1",  description: '<em>Changes the name for Door 1 displayed in dashboards, does not affect children unique deviceNetworkId.  Changing this will have no effect on the children devices being re-created.</em>'}
    if(doorCount > 1){input name: "d2Name", type: "string", title: "Door 2 Name", required: "false", defaultValue : "Door 2",  description: '<em>Changes the name for Door 2 displayed in dashboards, does not affect children unique deviceNetworkId.  Changing this will have no effect on the children devices being re-created.</em>'}
    if(doorCount == 3){input name: "d3Name", type: "string", title: "Door 3 Name", required: "false", defaultValue : "Door 3",  description: '<em>Changes the name for Door 3 displayed in dashboards, does not affect children unique deviceNetworkId.  Changing this will have no effect on the children devices being re-created.</em>'}
}

metadata {
    definition (
        name: "Tailwind Garage Door", 
		namespace: "dabtailwind-gd", 
		author: "dbadge"		
    ) {
        capability "Polling"
        attribute "Status", "string"
        command "childOpen", ["integer"]
        command "childClose", ["integer"]
    }
}



def installed() {
    //log.info "Clearing schedule for Polling interval"
    //unschedule()
    //init()    
}

def uninstalled() {
    getChildDevices().each { deleteChildDevice("${it.deviceNetworkId}") }
}

def updated() {    
    log.info "Clearing schedule for Polling interval"
    unschedule()
    //disable logging after 2 hours
    if (debugEnable) runIn(7200,disableDebug)
    init()
}

def init() {
    log.info "Scheduling Polling interval for ${settings.interval} minute(s)..."    
    addChildren()
    sendEvent(name: "Status", value: 0)
    //schedule("0/${settings.interval} * * ? * * *", poll)
    if (settings.interval == "1") runEvery1Minute(poll)
    else if (settings.interval == "5") runEvery5Minutes(poll)
    else if (settings.interval == "10") runEvery10Minutes(poll)
    else if (settings.interval == "15") runEvery15Minutes(poll)
    else if (settings.interval == "30") runEvery30Minutes(poll)
    poll()    
}

def disableDebug(String level) {
  log.info "Timed elapsed, disabling debug logging"
  device.updateSetting("debugEnable", [value: 'false', type: 'bool'])
}

void addChildren(){
    int dc = doorCount.toString().toInteger()
    //Cleanup any children that are no longer needed due to doorCount change or name mismatch
    getChildDevices().each {       
        spl = it.deviceNetworkId.split(':')       
        if(spl[0] != cName  || spl[1].toInteger() > dc){
            if(debugEnable) log.debug  "delete ${it.deviceNetworkId}"
            deleteChildDevice("${it.deviceNetworkId}")
            }
    } 
    //loop through up to doorCount to create children
    for (int c = 0; c < dc; c++) {
        def d = c + 1
        def dn=""
        if (d == 1){dn = d1Name}
        if (d == 2){dn = d2Name}
        if (d == 3){dn = d3Name}
        if(debugEnable) log.debug ("${cName}:${d}")
        def cd = getChildDevice("${cName}:${d}")
        if(!cd) {
            cd = addChildDevice("dabtailwind-gd","Tailwind Garage Door Child Device","${cName}:${d}", [label: "${cName} : ${dn}", name: "${d}", isComponent: true])         
            if(cd && debugEnable){
                log.debug "Child device ${cd.displayName} was created" 

            }else if (!cd){
                log.error "Could not create child device"
            }
        }
        if(debugEnable) log.debug "deviceNetworkId ${cd.deviceNetworkId}=${cName}:${d} name ${cd.name}=${d} label ${cd.label}=${cName} : ${dn}"        
        if(cd.label != "${cName} : ${dn}")
        {
            log.debug "Correcting child label mismatch ${cd.label}=${cName} : ${dn}"
            cd.label = "${cName} : ${dn}"
        }
        if(cd.name != "${d}")
        {
            log.debug "Correcting child name mismatch ${cd.name}=${d}"
            cd.name = "${d}"
        }
    }
    
}

def poll() {
    def s = checkStatus()
    def old = device.currentValue("Status").toInteger()
    //only set status if it changed. a lot less event spam this way.
    if(s != old)
    {
        log.debug "Status changed from ${old} to ${s}"
        setDoorStatus(s)
    }
}

def openClose(String command, Integer doorNumber){
    def desiredStatus = "closed"
    def Integer cmd = doorNumber * -1
    if(doorNumber == 3) cmd = cmd - 1 //assuming documentation is correct on -4 and 4 for door #3, another user reported this being 3, but mine still seems to be 4.
    if(command == "open")
    {
        desiredStatus = "open"
        cmd = cmd * -1
    }
    if(debugEnable) log.debug "Attempting to ${command} door ${doorNumber}"
    def postParams = [uri: "http://${IP}/cmd", body : "${cmd}"]     
    if(debugEnable) log.debug postParams
    httpPost(postParams) { def resp ->
        if(debugEnable) log.debug "${command} Response (should match ${cmd}): ${resp.data}"     
        if ("${resp.data}" == "${cmd}" )
        {
            log.debug "in 1 second, start polling every 5 seconds for door to open/close."
            //schedule to run the refresh for rapid updates on dashboard
            runIn(1, "postActionRefresh", [data:["desiredStatus":"${desiredStatus}","doorNumber":doorNumber]])
        }
    }
}

void postActionRefresh(data){
    def Integer loopSpeed = 2
    log.debug "Now polling every ${loopSpeed} seconds for door to open/close."
    String desiredStatus = data.get("desiredStatus")
    def Integer doorStatus = checkStatus()
    Integer doorNumber = data.get("doorNumber").toInteger()
    if(debugEnable) log.debug "${doorStatus} Door #${doorNumber} Desired Status: ${desiredStatus} Current status: ${doorCheck(doorNumber,doorStatus)}"
    def Integer i = 0 //count seconds elapsed after door command
    
    while ( doorCheck(doorNumber, doorStatus) != desiredStatus){                
        doorStatus = checkStatus()
        if(debugEnable) log.debug "Door #${doorNumber} Desired Status: ${desiredStatus} Current status: ${doorCheck(doorNumber,doorStatus)}"
        pauseExecution(loopSpeed * 1000)
        //break out of loop after a period, infinite loops are bad
        i += loopSpeed
        if(i >= garageDoorTimeout)
        {
            if(debugEnable) log.debug "${garageDoorTimeout} seconds is too long for a door, probably something went wrong physically (blocked sensor, stuck/etc)."
            break
        }
    }
    if (doorCheck(doorNumber, doorStatus) == desiredStatus){setDoorStatus(doorStatus)}
    
    if(debugEnable) log.debug "Door #${doorNumber} Desired Status: ${desiredStatus} Current status: ${doorCheck(doorNumber,doorStatus)}"          
}

def doorCheck(Integer doorNumber, Integer doorStatus){
    checkNumber = doorNumber -1 //the statusCode is 0,1,2 so subtract 1 
    r =getDoorOpenClose(getDoorStatus(doorStatus,checkNumber))
    if(debugEnable) log.debug "trying to get open/close from ${doorStatus} ${checkNumber}"
    if(debugEnable) log.debug "getDoorStatus returned ${getDoorStatus(doorStatus,checkNumber)}"
    if(debugEnable) log.debug "Door ${doorNumber} is ${r}"
    return r
}

def childOpen(Integer doorNumber){
    open(doorNumber)
}

def childClose(Integer doorNumber){
    close(doorNumber)
}

def open(Integer doorNumber) {
    openClose("open",doorNumber)
}

def close(Integer doorNumber) {   
   openClose("close",doorNumber)
}

def checkStatus() {
    httpGet(uri: "http://${ IP }/status")
    {resp ->           
        if(debugEnable) log.debug "POST Door Status: ${resp.data}"        
        return resp.data.toInteger()
	}
    
}

void setDoorStatus(status){
    if(debugEnable) log.debug "Setting Door Status attribute to ${status}"
    sendEvent(name: "Status", value: status)
    for(int i =0; i < doorCount.toInteger(); i++){
        ds = getDoorStatus(status,i)
        dStatus = getDoorOpenClose(ds)
        if(debugEnable) log.debug "Real door ${i+1} is ${ds} ${dStatus}"      
        setChildStatus(i+1, dStatus)
    }   
}

def getDoorStatus(Integer status, Integer door){
        statusCodes=[
          [-1, -2, -4],   
          [1, -2, -4],    
          [-1, 2, -4],    
          [1, 2, -4],     
          [-1, -2, 4],    
          [1, -2, 4],     
          [-1, 2, 4],     
          [1, 2, 4]
        ] 
    return statusCodes[status][door]
}

void setChildStatus(dNum, status){
    def cd = getChildDevice("${cName}:${dNum}")        
    if(cd.latestValue("door") == status){
        if(debugEnable) log.debug "Child device ${cName}:${dNum} Matches real door"
    }
    else{
        if(debugEnable) log.debug "Child device ${cName}:${dNum} DOESN'T match real door, update child to match"
        cd.sendEvent(name:"door", value:"${status}")
    }    
}

def getDoorOpenClose(Integer curStatus)
{
    if(curStatus < 0){
        return "closed"
    }
    else{
        return "open"
    }    
}
