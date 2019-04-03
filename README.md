# ![](app/src/main/res/mipmap-hdpi/ic_launcher.png) Webthingify
[![Liberapay](https://img.shields.io/liberapay/receives/freaktechnik.svg?logo=liberapay)](https://liberapay.com/freaktechnik/donate) [![PayPal](https://img.shields.io/badge/PayPal-Tip-blue.svg?logo=paypal)](https://www.paypal.me/freaktechnik
)

Turn your Android phone into a Web of Things things

## Usage

The app UI offers a text field to choose the port the web thing server will run on and a toggle to
start and stop the server.

### Supported attributes

- Vibrate phone
- Toggle flashlight
- See pictures from front and back camera
- Real-time sensor values (brightness, distance, in motion, temperature, pressure, humidity)
- Phone power (battery level, charging)

### Use cases

- Surveillance camera with small blind spot if device has a front and back camera
- Weather station (depending on sensors; I'd suggest not giving the camera permission in that case, to lower the load on the device)
- Daylight sensor (brightness sensor)
- Door or window opening sensor (not quite implemented nicely yet, but movement or distance properties can be used for it)
- Power outage detector (charging property)
- I'm sure the vibration action could be used for something.

## How to build

Uh, open it in Android Studio and it'll hopefully just work?

## What android versions does this support

It runs on my Nexus 5X (with 8.1) and I haven't been able to make it run properly on my older phone that runs 6.
