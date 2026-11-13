# AniWatch Android — Native UI Prototype

A Kotlin + Jetpack Compose streaming-app shell designed for Android phones and Google TV. The interface uses a dark, Netflix-inspired layout with featured content, horizontal title rails, search, My List, detail pages, D-pad focusable controls, and a native AndroidX Media3 player.

## Important integration boundary

The current project does **not** extract video URLs from AniWatch's third-party embeds or bypass DRM, access controls, or provider restrictions. The AniWatch site is linked as a catalog reference. The home screen is prefilled with a small static snapshot of titles visible on the AniWatch homepage when this project was prepared. It does not yet refresh automatically from the site or provide live episode metadata. A stable, documented and authorized metadata endpoint is needed for reliable live catalog integration.

The native Media3 player accepts direct HTTP(S) video URLs for formats supported by Media3, including HLS (`.m3u8`), DASH (`.mpd`) and MP4. A webpage URL, iframe URL, or protected player page is not necessarily a direct media stream. Use only streams you are authorized to access.

## Requirements

- Android Studio Ladybug or newer
- JDK 17
- Android SDK Platform 35
- Gradle 8.9 (the GitHub Actions workflow installs it)

## Build in Android Studio

Open the `AniWatchAndroid` folder and allow Gradle to sync. If Android Studio asks for a Gradle distribution, select Gradle 8.9. Then select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Build on GitHub

Push the project contents to a GitHub repository. The included workflow `.github/workflows/build-apk.yml` builds the debug APK on pushes to `main`/`master` or when manually started from the Actions tab. Download the `AniWatch-Android-APK` artifact.

## TV and phone

The same APK declares both `LAUNCHER` and `LEANBACK_LAUNCHER` entry points. Compose controls are focusable for remote navigation and touch-enabled for phones. Test on actual target devices because TV focus behavior and playback depend on the device and stream format.
