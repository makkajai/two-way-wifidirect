# two-way-wifidirect
This repository will hold an example project that demonstrates how we could communicate between two Android devices using Socket NIO and WiFiDirect. The base code was taken from [here](https://android.googlesource.com/platform/development/+/master/samples/WiFiDirectDemo).

# How to run it
* Install this app on two Android device (WiFi Direct dosent work on the simulator)
* Turn on WiFi Direct from the Settings -> Wi-Fi -> Advanced Settings -> Wi-Fi Direct
* As soon as the app installs on the device it starts looking for Peers
* Once the Peers are found they are listed on the App
* Clicking on available device will initiate the connection
* Once connection is successful you should see two buttons `Disconnect` and `Send Message`
* Clicking on `Disconnect` with disconnect the channel and start looking for peers again
* Clicking on `Send Message` will send a sample message to the connected device.
* The communication between the devices is done using Socket NIO and to make sure everything is working fine check `LogCat`
