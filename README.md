# BluePair

BluePair is an Android app scaffold for controlling a two-speaker Bluetooth stereo setup.

## Implemented MVP structure

- Main screen with a large centered toggle button (`Phone Audio` / `Bluetooth Stereo`)
- Configure screen to pick up to two paired Bluetooth devices
- Channel assignment screen to assign Left/Right per selected device
- About screen with GitHub link (`rohittp0/BluePair`)
- Foreground routing service with persistent notification
- Quick Settings tile to toggle mode
- Home screen widget to toggle mode
- Shared app state via `SharedPreferences` across app/service/tile/widget

## Important platform note

Android public APIs do not provide a stable way for third-party apps to force-connect and route media simultaneously to two independent classic Bluetooth speakers as true L/R channels on all devices.

This scaffold wires the complete app flow and state management. The `BluetoothRoutingService` currently validates configured paired devices and maintains active mode/notification, but device-specific stereo routing behavior still needs platform-specific implementation and testing.

## Build

```bash
./gradlew :app:compileDebugKotlin
```
