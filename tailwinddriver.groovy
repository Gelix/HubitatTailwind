
preferences {
    input name: "IP", type: "string", title: "Tailwind Controller IP", required: "True"
    //input name: "token", type: "password", title: "Access Token", required: "True"
    input name: "doorCount", type: "integer", title: "Number of Doors", required: "True"
    input name: "debugEnable", type: "bool", title: "Enable debug logging?", required: "True"
    input name: "interval", type: "integer", title: "Polling interval (seconds)", required: "True"
}

metadata {
    definition (
        name: "Tailwind Garage Door", 
		namespace: "dabtailwind-gd", 
		author: "dbadge"		
    ) {
        capability "Polling"
        attribute "Status", "string"
        command "childOpen", ["string"]
        command "childClose", ["string"]
        command "childRefresh", ["string"]
    }
}

def installed() {
    log.info "Clearing schedule for Polling interval"
    unschedule()
    init()
}

def uninstalled() {
    getChildDevices().each { deleteChildDevice("${it.deviceNetworkId}") }
}

def updated() {
    log.info "Clearing schedule for Polling interval"
    unschedule()
    init()
}

def init() {
    log.info "Scheduling Polling interval for ${settings.interval} second(s)..."    
    addChildren()
    schedule("0/${settings.interval} * * ? * * *", poll)
    poll()
}

void addChildren(){
    int dc = doorCount.toString().toInteger()
    //Cleanup any children that are no longer needed due to doorCount change
    getChildDevices().each {         
        if(it.deviceNetworkId[-1].toInteger() > dc){
            if(debugEnable) log.debug  "delete ${it.deviceNetworkId[-1]}"
            deleteChildDevice("${it.deviceNetworkId}")
            }
    } 
    //loop through up to doorCount to create children
    for (int c = 0; c < dc; c++) {
        def d = c + 1
        if(debugEnable) log.debug ("${IP} : Door ${d}")
        def cd = getChildDevice("${IP} : Door ${d}")
        if(!cd) {
            cd = addChildDevice("dabtailwind-gd","Tailwind Garage Door Child Device","${IP} : Door ${d}", [label: "${IP} : Door ${d}", name: "${IP} : Door ${d}", isComponent: true])
            if(cd && debugEnable){
                log.debug "Child device ${cd.displayName} was created"
            }else if (!cd){
                log.error "Could not create child device"
            }
        }
    }
}

def poll() {
    checkStatus()
   // syncChildren() 
}

def open(Integer doorNumber) {
    def postParams = [uri: "http://${IP}/cmd", body : "${doorNumber}"]     
        httpPost(postParams) { resp ->
           if(debugEnable) log.debug "Open Response: ${resp.data}"     
            if ("${resp.data}" == "${doorNumber}" )
            {
                checkStatus()
            }
    }
}

def close(Integer doorNumber) {   
    def postParams = [uri: "http://${IP}/cmd", body : "-${doorNumber}"]     
        httpPost(postParams) { resp ->
            if(debugEnable) log.debug "Close Response: ${resp.data}"
            if ("${resp.data}" == "-${doorNumber}" )
            {
                checkStatus()
            }
    }
}


/*void syncChildren(){
    //this does nothing ATM.
    getChildDevices().each {
        if(debugEnable) log.debug "${it}  ${it.latestValue("door")}"        
    }
}*/

def checkStatus() {
    httpGet(uri: "http://${ IP }/status")
    {resp ->           
        if(debugEnable) log.debug "Door Status: ${resp.data}"  
        
        doorStatus(resp.data.toInteger())
	}

}

void doorStatus(status){
    
     def statusCodes=[
  [-1, -2, -4],   //0
  [1, -2, -4],    //1
  [-1, 2, -4],    //2
  [1, 2, -4],     //3
  [-1, -2, 4],    //4
  [1, -2, 4],     //5
  [-1, 2, 4],     //6
  [1, 2, 4]       //7
] 
    if(debugEnable) log.debug "Setting Attributes"
    sendEvent(name: "Status", value: status)
    for(int i =0; i < doorCount.toInteger(); i++){
        retVal = statusCodes[status][i]
        dStatus = getDoorOpenClose(retVal)
        if(debugEnable) log.debug "Real door ${i+1} is ${retVal} ${dStatus}"        
        setChildStatus(i+1, dStatus)
    }   
}

void childClose(String dni){
    if(debugEnable) log.debug "Attempting to close door ${dni}"
    def cd = getChildDevice(dni)
    def door = dni[-1].toInteger()
    close(door)
    //based on timing, one of my doors averages about 25-30 seconds to close. Tailwind beeps for a while, then the door closes.
    runIn(25000,poll)
}

void childOpen(String dni){
    if(debugEnable) log.debug "Attempting to open door ${dni}"
    def cd = getChildDevice(dni)
    def door = dni[-1].toInteger()
    open(door)
    //depends on the door sensor placement, most doors are near instant
    runIn(3000, poll)
}

void setChildStatus(dNum, status){
    def cd = getChildDevice("${IP} : Door ${dNum}")        
    if(cd.latestValue("door") == status){
        if(debugEnable) log.debug "Child device ${IP} : Door ${dNum} Matches real door"
    }
    else{
        if(debugEnable) log.debug "Child device ${IP} : Door ${dNum} DOESN'T match real door, update child to match"
        cd.sendEvent(name:"door", value:"${status}")
    }    
}

def getDoorOpenClose(curStatus)
{
    if(curStatus < 0){
        return "closed"
    }
    else{
        return "open"
    }    
}
