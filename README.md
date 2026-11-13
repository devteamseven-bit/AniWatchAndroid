# AniWatch Android / Google TV wrapper

A lightweight Android WebView app that opens https://aniwatch.co.at/ and exposes both the regular Android launcher and Android TV Leanback launcher entries.

## Build

Requirements: JDK 17, Android SDK Platform 35, Android build-tools, Gradle 8.9.

```bash
gradle assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

You can also push this folder to a GitHub repository and run **Actions → Build AniWatch APK**. The workflow uploads the APK as an artifact.

## Install

- Android phone: transfer the APK and install it (allow installation from that file manager if prompted).
- Google TV / Android TV: transfer the APK to the TV and install it with a file manager, or use `adb install app-debug.apk` from a computer on the same network with ADB debugging enabled.

## Notes

- This is a website wrapper, not an official AniWatch app. It does not host or download video content.
- Playback depends on the website, its embedded video providers, and the Android System WebView/Chrome version on the device. Some providers may not support TV WebView playback or remote-control navigation.
- The app keeps website navigation inside the WebView so episode pages and embedded players can work. It denies mixed HTTP content and does not grant camera/microphone permissions.
- The app is not signed with a personal release key. The GitHub workflow creates a debug APK suitable for testing/sideloading. A release APK should be signed with your own key before distribution.
