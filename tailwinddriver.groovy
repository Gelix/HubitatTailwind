
preferences {
    input name: "IP", type: "text", title: "TW Device IP", required: "True"
    //input name: "token", type: "password", title: "Access Token", required: "True"
    input name: "doorCount", type: "text", title: "Number of Doors", required: "True"
    input name: "doorNumber", type: "enum", title: "Active Door", required: "True", options: ["1", "2", "4"] //TODO: replace with children devices.
    input name: "debugEnable", type: "bool", title: "Enable debug logging?", required: "True"
    input name: "interval", type: "number", title: "Polling interval (seconds)", required: "True"
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
    
    //getChildDevices().each { deleteChildDevice("${it.deviceNetworkId}") }
    addChildren()
    schedule("0/${settings.interval} * * ? * * *", poll)
    poll()
}

void addChildren(){
    int dc = doorCount.toString().toInteger()
    getChildDevices().each { 
        
        if(it.deviceNetworkId[-1].toInteger() > dc){
            log.debug  "delete ${it.deviceNetworkId[-1]}"
            deleteChildDevice("${it.deviceNetworkId}")
            }
    }   
    for (int c = 0; c < dc; c++) {
        def d = c + 1
        log.debug ("${IP} : Door ${d}")
        def cd = getChildDevice("${IP} : Door ${d}")
        if(!cd) {
            cd = addChildDevice("hubitat","Virtual Garage Door Controller","${IP} : Door ${d}", [label: "${IP} : Door ${d}", name: "${IP} : Door ${d}", isComponent: true])
            if(cd && debugEnable){
                log.debug "Child device ${cd.displayName} was created"
            }else if (!cd){
                log.error "Could not create child device"
            }
        }
    }
    
        
    /*(if(door1 && currentchild==null) {
        currentchild = addChildDevice("Tailwind Garagedoor", "1", [isComponent: true, name: "Garage Door 1", label: "Garage Door 1"])
    } else if (!door1 && currentchild!=null) {
        deleteChildDevice("1")
    }
    */
}

void updateChildren() {
    getChildDevices().each { 
        it.setIP(ip)
        it.setDoorID(it.deviceNetworkId)
    }
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
    syncChildren() 
}

void syncChildren(){
    def cd = getChildDevice("${IP} : Door 1")
    if(debugEnable) log.debug "${cd}  ${cd.currentContact}"        
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
    if(debugEnable) log.debug "Door ${doorNumber} is ${retVal}"
    sendEvent(name: "door", value: getDoorOpenClose(retVal))
    for(int i =0; i<doorCount.toInteger(); i++){
        retVal = statusCodes[status][i]
        dStatus = getDoorOpenClose(retVal)
        if(debugEnable) log.debug "Real door ${i+1} is ${retVal} ${dStatus}"
        sendEvent(name: "d${i+1}", value: dStatus)
        setChildStatus(i+1, dStatus)
    }   
}
def singleDoorStatus(dNum){
    
}

void setChildStatus(dNum, status){
    try{
        def cd = getChildDevice("${IP} : Door ${dNum}")
        if(cd.currentContact == status){
            //if(debugEnable) log.debug "Match"
        }
        else{
            if(debugEnable) log.debug "MisMatch"
            cd.toggle
        }    
    }
    finally{}
    
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
