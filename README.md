# SignalMapper
A mobile signal strength tool for Android which maps dbm measurements to location, and performs aggregate statisitics.

Written for my mobile communications course during the 2022 Winter semester.

## Contents
The contents of this repo includes the source code for the Android app, along with it's accompanying tehcnical report named `report.pdf`. 
The app was written with the Jetpack Compose UI framework. 

## Usage
SignalMapper allows for the recording of dbm mesaurements along with GPS coordinates, which it maps onto a Google Maps component. 
This means that an api key for Google Maps services, which can be obtained for free, will be required. This key should be placed in a `local.proporties` file at the root 
of this directory like so:
``` groovy
MAPS_API_KEY={key}
```

The app sports the following features:
- Background capture - once the app starts capturing, it can be left in the background and the phone can be locked.
- Aggregate statistics - Measurements taken within close range of eachother are grouped into an aggregate measurement. This declutters the map and measurement list, and 
enables basic aggregate statistics to be performed on the group to get a more fleshed out measurement of the area. 
- Export and Import - Measurements can be exported as JSON, preserving aggregate data structures. This allows for external processing. 
