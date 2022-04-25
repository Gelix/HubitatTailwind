Hubitat device drivers for local Tailwind API control

Tailwind has offical firmware version 9.95 for the controller that enables local API control (along with Apple homekit).  This driver supports the local API control of the controllers. This firmware version implements a token authentication mechanism for local API control. 

Installation instructions:

1. Create a new driver for the Tailwind controller using https://raw.githubusercontent.com/Gelix/HubitatTailwind/main/tailwinddriver.groovy
2. Create a new driver for the child device using https://raw.githubusercontent.com/Gelix/HubitatTailwind/main/tailwinddriver-child.groovy
3. Create a virtual device on Hubitat with Tailwind Garage Door as the device type
4. Set the Tailwind Controller IP of your controller and the number of doors it controls, along with the Controller Name.
5. Login with your tailwind app credentials to https://web.gotailwind.com/, go to Local Control Key, create a new local command key. This is per-account and is the same for each device you may have on your account. Enter this in the driver as Local Command Key.
6. Start using. You can assign the children devices (named ControllerName : DoorName) to a dashboard to control them directly, or use in rules.


If you have duplicate drivers in the hubitat, you were probably using a version prior to 1.0.3 which lacked a hard coded ID.  Delete the drivers that aren't in use and use HPM to "Match up" with an existing driver, then use HPM to update the package.  
