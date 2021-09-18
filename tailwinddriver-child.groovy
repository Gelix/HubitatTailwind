metadata {
    definition (
        name: "Tailwind Garage Door Child Device", 
		namespace: "dabtailwind-gd", 
		author: "dbadge"		
    ) {
        capability "GarageDoorControl"
        attribute "Status", "string"
        command "open"
        command "close"
    }
}

void close(){
    if(debugEnable) log.debug "Child says Door #${device.deviceNetworkId[-1].toInteger()} to Close"
    parent.close(device.deviceNetworkId[-1].toInteger())   
}

void open(){
    if(debugEnable) log.debug "Child says Door #${device.deviceNetworkId[-1].toInteger()} to Open"
    parent.open(device.deviceNetworkId[-1].toInteger())   
}
