# Building

To build the app use `./gradlew clean build`

# Run

1. Push the `defaultConfig.properties` file to `/sdcard/` as we cannot load it as a resource by default using Konfig.

```
adb push app/src/main/res/defaultConfig.properties /sdcard/
```

2. Start the tool

3. Accept the permissions

4. Select an app

# Notes

1. The app works better on Android 10, as the accessibility service seems to be more stable

2. The results are stored on `/sdcard/DM-2/`