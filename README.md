# AniWatch Native Android / Google TV App

A native Kotlin + Jetpack Compose anime app for Android phones and Google TV. It is not a WebView wrapper and it does not use a hardcoded sample catalog.

## What is implemented

- Live home sections from an AniWatch-compatible API: Spotlight, Trending, Top Airing, Latest Episodes, Popular, Favorites and Completed.
- Real poster artwork loaded from API image URLs.
- Search against the API, with a short debounce.
- Anime detail pages and episode lists.
- Episode server lookup and source lookup through the API.
- Native Media3 / ExoPlayer HLS playback, including source Referer/User-Agent headers and the first available subtitle track.
- My List, phone touch navigation and focusable controls for TV remotes.
- API base URL setting so the backend can be changed without changing the Android code.
- GitHub Actions workflow to build a debug APK.

## Important: the API is a separate service

This Android project does not contain an API server. The app expects an HTTPS API server implementing the documented HiAnime/AniWatch-compatible routes under `/api/v2/hianime`.

The initial adapter targets the API contract documented by:

- https://github.com/VoidX3D/hianime-api

That project describes itself as an unofficial scraper for **hianimez.to**, not an official API for `aniwatch.co.at`. Its README recommends deploying your own instance; it warns that its former hosted demo is no longer available. Therefore, this app cannot promise that the API will return the exact same catalog as `aniwatch.co.at`, or that every episode will have a working stream. API/provider changes can break results.

### Deploy your own API instance

1. Open https://github.com/VoidX3D/hianime-api and fork the repository.
2. Deploy your fork using its current hosting instructions (Docker, Render or Vercel are described in that repository).
3. For Render, follow the upstream README and set `ANIWATCH_API_DEPLOYMENT_ENV=render`. For Vercel, set it to `vercel`.
4. Wait until the deployment reports healthy and copy its HTTPS base URL, for example `https://your-service.example` (do not append `/api/v2/hianime`).
5. Install the Android app, open **Settings**, paste that HTTPS base URL and tap **Save and connect**.

Do not use an untrusted public API host for your personal viewing data. The Android app sends catalog and playback lookup requests to the API address you configure.

## Playback notes

The API contract returns HLS source URLs, request headers and subtitle tracks. The app hands those values to Media3. It does not decrypt DRM, defeat provider authentication, or turn unsupported embedded players into direct streams. If an upstream server returns an embed instead of a playable HLS source, that episode will show a playback error rather than asking you to paste a URL.

Only use sources and content you are authorized to access. Availability, regional restrictions and provider uptime are controlled by the upstream services, not this app.

## Build

- Android Studio with JDK 17
- Android Gradle Plugin 8.7.3 / Gradle 8.9
- Android SDK 35

Open the `AniWatchAndroid` folder in Android Studio. If Android Studio asks for a Gradle installation, use Gradle 8.9. The included GitHub Actions workflow installs Gradle 8.9 and builds `app/build/outputs/apk/debug/app-debug.apk`.

The project source has not been compiled in this environment because an Android SDK / Gradle installation is not available here. Run the GitHub Actions build or build locally in Android Studio before installing it.
