
preferences {
    input name: "IP", type: "string", title: "Tailwind Controller IP", required: "True"
    input name: "cName", type: "string", title: "Tailwind Controller Name", required: "True"
    //input name: "token", type: "password", title: "Access Token", required: "True"
    input name: "doorCount", type: "number", title: "Number of Doors", required: "True", range: "0..3", defaultValue : 1
    input name: "debugEnable", type: "bool", title: "Enable debug logging?", required: "True"
    input name: "interval", type: "number", title: "Polling interval (seconds)", required: "True", range: "1..59", defaultValue : 30
    input name: "d1Name", type: "string", title: "Door 1 Name", required: "false", defaultValue : "Door 1"
    input name: "d2Name", type: "string", title: "Door 2 Name", required: "false", defaultValue : "Door 2"
    input name: "d3Name", type: "string", title: "Door 3 Name", required: "false", defaultValue : "Door 3"
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
        if(debugEnable) log.debug "deviceNetworkId ${cd.deviceNetworkId}=${cName}:${d}"        
        if(debugEnable) log.debug "name ${cd.name}=${d}"
        if(debugEnable) log.debug "label ${cd.label}=${cName} : ${dn}"
        if(cd.label != "${cName} : ${dn}")
        {
            log.debug "label mismatch"
            cd.label = "${cName} : ${dn}"
        }
        if(cd.label != "${cName} : ${dn}")
        {
            log.debug "name mismatch"
            cd.name = "${d}"
        }
    }
    
}

def poll() {
    checkStatus()   
}

def open(Integer doorNumber) {
    def postParams = [uri: "http://${IP}/cmd", body : "${doorNumber}"]     
    httpPost(postParams) { resp ->
        if(debugEnable) log.debug "Open Response: ${resp.data}"     
        if ("${resp.data}" == "${doorNumber}" )
        {
            Integer s=-2 //checkStatus()
            
            while (getDoorOpenClose(getDoorStatus(s,doorNumber)) != "open"){
                if(debugEnable) log.debug "Current status ${getDoorOpenClose(getDoorStatus(s,doorNumber))}"
                s = checkStatus()
                if(debugEnable) log.debug "Current status ${getDoorOpenClose(getDoorStatus(s,doorNumber))}"
                pauseExecution(1000)
            }     
        }
    }
}

def close(Integer doorNumber) {   
    def postParams = [uri: "http://${IP}/cmd", body : "-${doorNumber}"]     
    httpPost(postParams) { resp ->
        if(debugEnable) log.debug "Close Response: ${resp.data}"
        if ("${resp.data}" == "-${doorNumber}" )
        {   
            Integer s=2 //checkStatus()
            
            while (getDoorOpenClose(getDoorStatus(s,doorNumber)) != "closed"){
                if(debugEnable) log.debug "Current status ${getDoorOpenClose(getDoorStatus(s,doorNumber))}"
                s = checkStatus()
                if(debugEnable) log.debug "Current status ${getDoorOpenClose(getDoorStatus(s,doorNumber))}"
                pauseExecution(1000)
            }            
        }
    }
}

def checkStatus() {
    httpGet(uri: "http://${ IP }/status")
    {resp ->           
        if(debugEnable) log.debug "Door Status: ${resp.data}"
        doorStatus(resp.data.toInteger())
        return resp.data.toInteger()
	}
    
}

void doorStatus(status){
    if(debugEnable) log.debug "Setting Attributes"
    sendEvent(name: "Status", value: status)
    for(int i =0; i < doorCount.toInteger(); i++){
        getDoorStatus(status,i)
        dStatus = getDoorOpenClose(retVal)
        if(debugEnable) log.debug "Real door ${i+1} is ${retVal} ${dStatus}"        
        setChildStatus(i+1, dStatus)
    }   
}

def getDoorStatus(Integer status, Integer door){
        statusCodes=[
          [-1, -2, -4],   //0
          [1, -2, -4],    //1
          [-1, 2, -4],    //2
          [1, 2, -4],     //3
          [-1, -2, 4],    //4
          [1, -2, 4],     //5
          [-1, 2, 4],     //6
          [1, 2, 4]       //7
        ] 
     retVal = statusCodes[status][door]
    return retVal
}

void childClose(String dni){
    if(debugEnable) log.debug "Attempting to close door ${dni}"
    def cd = getChildDevice(dni)
    def door = cd.name
    close(door)
}

void childOpen(String dni){
    if(debugEnable) log.debug "Attempting to open door ${dni}"
    def cd = getChildDevice(dni)
    def door = dni[-1].toInteger()
    open(door)
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

def getDoorOpenClose(curStatus)
{
    if(curStatus < 0){
        return "closed"
    }
    else{
        return "open"
    }    
}
