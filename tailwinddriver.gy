
preferences {
    input name: "IP", type: "text", title: "TW Device IP", required: "True"
    //input name: "token", type: "password", title: "Access Token", required: "True"
    input name: "doorCount", type: "text", title: "Number of Doors", required: "True"
    input name: "doorNumber", type: "enum", title: "Current Door", required: "True", options: ["1", "2", "4"]
    input name: "debugEnable", type: "bool", title: "Enable debug logging?", required: "True"
    input name: "interval", type: "enum", title: "Polling interval", required: "True", options: ["1", "5" , "10", "15", "30"]
}

metadata {
    definition (
        name: "Tailwind Garage Door", 
		namespace: "dabtailwind-gd", 
		author: "dbadge"		
    ) {
        capability "GarageDoorControl"
        capability "Polling"
        attribute "Status", "string"
        attribute "d1", "string"
        attribute "d2", "string"
        attribute "d3", "string"
    }
}
def installed() {
    log.info "Clearing schedule for Polling interval"
    unschedule()
    init()
}
def updated() {
    log.info "Clearing schedule for Polling interval"
    unschedule()
    init()
}
def init() {
    log.info "Scheduling Polling interval for ${settings.interval} minute(s)..."
    switch(interval.toInteger()){
        case 1:
            runEvery1Minute(poll)
            log.info "Scheduled ${settings.interval} minute(s)..."
            break
        case 5:
            runEvery5Minutes(poll)
            log.info "Scheduled ${settings.interval} minute(s)..."
            break
        case 10:
            runEvery10Minutes(poll)
            log.info "Scheduled ${settings.interval} minute(s)..."
            break
        case 15:
            runEvery15Minutes(poll)
            log.info "Scheduled ${settings.interval} minute(s)..."
            break
        case 30:
            runEvery30Minutes(poll)
            log.info "Scheduled ${settings.interval} minute(s)..."
            break
    }
    poll()
}

def open() {
    def postParams = [uri: "http://${IP}/cmd", body : "${doorNumber}"]     
        httpPost(postParams) { resp ->
           if(debugEnable) log.debug "Open Response: ${resp.data}"     
            if ("${resp.data}" == "${doorNumber}" )
            {
                checkStatus()
            }
    }
}

def close() {
   
    def postParams = [uri: "http://${IP}/cmd", body : "-${doorNumber}"]     
        httpPost(postParams) { resp ->
            if(debugEnable) log.debug "Close Response: ${resp.data}"
            if ("${resp.data}" == "-${doorNumber}" )
            {
                checkStatus()
            }
    }
}

def poll() {
    checkStatus()
}

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
    int retVal = statusCodes[status][doorNumber.toInteger() - 1] 
    if(debugEnable) log.debug "door ${doorNumber} is ${retVal}"
    sendEvent(name: "door", value: getDoorOpenClose(retVal))
    retVal = statusCodes[status][0]
    if(debugEnable) log.debug "door 1 is ${retVal}"
    sendEvent(name: "d1", value: getDoorOpenClose(retVal))
    retVal = statusCodes[status][1]
    if(debugEnable) log.debug "door 2 is ${retVal}"
    sendEvent(name: "d2", value: getDoorOpenClose(retVal))
    retVal = statusCodes[status][2]
    if(debugEnable) log.debug "door 3 is ${retVal}"
    sendEvent(name: "d3", value: getDoorOpenClose(retVal))   
    
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
