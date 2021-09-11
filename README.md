Hubitat device drivers for local Tailwind API control

Tailwind has beta firmware for the controller that enables local API control (along with Apple homekit I think).  This driver supports the local API control of the controllers. There currently is no authentication mechanism with this beta firmware for local API control. 

Installation instructions:

1. Create a new driver for the Tailwind controller using https://raw.githubusercontent.com/Gelix/HubitatTailwind/main/tailwinddriver.groovy
2. Create a new driver for the child device using https://raw.githubusercontent.com/Gelix/HubitatTailwind/main/tailwinddriver-child.groovy
3. Create a virtual device on Hubitat with Tailwind Garagedoor Controller as the device type
4. Set the Tailwind IP of your controller and the number of doors it controls, along with the polling interval.
5. Start using. You can assign the children devices (named <ip of controller : DoorNumber> to a dashboard to control them directly, or use in rules.
