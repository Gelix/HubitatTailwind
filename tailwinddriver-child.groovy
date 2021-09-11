metadata {
    definition (
        name: "Tailwind Garage Door Child Device", 
		namespace: "dabtailwind-gd", 
		author: "dbadge"		
    ) {
        capability "GarageDoorControl"
        capability "Refresh"
        attribute "Status", "string"
        command "open"
        command "close"
    }
}

void close(){
     parent.childClose(device.deviceNetworkId)   
}

void open(){
     parent.childOpen(device.deviceNetworkId)   
}

void refresh() {
     parent.childRefresh(device.deviceNetworkId)
}
