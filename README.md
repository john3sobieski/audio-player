# Local Player

A simple, native Android music player for your **local audio files**. It only plays music from a single folder that you choose in the settings, so ringtones, voice messages, and other sounds on your phone never end up in your library.

Built with Kotlin, Jetpack Compose, and Media3 (ExoPlayer). No ads, no accounts, no internet access required.

## Features

- **Single music folder.** Pick a folder in Settings; the app scans it and its subfolders. It uses Android's Storage Access Framework, so it never requests access to your whole storage and cannot see anything outside the folder you picked.
- **Three library sections:**
  - **Playlists**: playlists you create yourself (create, rename, delete, add and remove tracks)
  - **Albums**: grouped automatically from the files' tags
  - **Tracks**: all tracks in one mixed list
- **Background playback** with a media notification, lock screen controls, and headset button support.
- **Player controls:** play/pause, next/previous, seek, shuffle, repeat (off / all / one).
- **Cover art** read from embedded tags.
- **Fast rescans:** track metadata is cached, so reopening the app or rescanning a large folder is quick.
- Supported formats: MP3, FLAC, OGG/Opus, M4A/AAC, WAV, and other formats supported by Android.
- Material 3 design with dynamic colors on Android 12+ and light/dark theme support.

## Requirements

- Android 8.0 (API 26) or newer

## Download and install

Build the APK yourself using one of the methods below, then copy `app-debug.apk` to your phone, open it, and allow installation from unknown sources when prompted.

## Building from source

### Option 1: Android Studio

1. Install [Android Studio](https://developer.android.com/studio). It bundles the JDK and the Android SDK.
2. Clone this repository and open the project folder (**File → Open**).
3. Wait for the Gradle sync to finish (the first sync downloads dependencies and can take several minutes).
4. Choose **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
5. The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

You can also run the app directly on a connected device or an emulator with the **Run** button.

### Option 2: Command line

You need JDK 17 and the Android SDK (set `ANDROID_HOME`), plus Gradle 8.9 or newer:

```bash
gradle assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### Option 3: GitHub Actions (no local setup)

This repository includes a workflow in `.github/workflows/build.yml`.

1. Fork this repository or push it to your own GitHub account.
2. Open the **Actions** tab, select **Build APK**, and click **Run workflow** (it also runs on every push to `main`/`master`).
3. When the run finishes, download the **LocalPlayer-debug-apk** artifact. It contains `app-debug.apk`.

> The debug APK is signed with the default Android debug key. That is fine for personal use. To distribute a release build, configure your own signing key.

## How to use

1. Open the app and tap **Choose folder** (or go to **Settings**).
2. Select the folder that contains your music. The app scans it and fills the library.
3. Browse the **Playlists**, **Albums**, and **Tracks** tabs and tap a track to play.
4. To build a playlist, open the **⋮** menu next to any track and choose **Add to playlist**.
5. If you add new files to the folder, open **Settings** and tap **Rescan folder**.

## Project structure

```
app/src/main/java/com/example/localplayer/
├── MainActivity.kt        # Entry point, theme, notification permission
├── Ui.kt                  # All screens (Compose)
├── PlayerViewModel.kt     # App state, playlists, player control
├── Library.kt             # Folder scanning, metadata cache, playlist storage
├── PlaybackService.kt     # Background playback (Media3 MediaSessionService)
└── Models.kt              # Track / Album / Playlist data classes
```

Playlists are stored locally as JSON in the app's private storage. Nothing leaves your device.

## Tech stack

- Kotlin 2.0, Jetpack Compose, Material 3
- AndroidX Media3 (ExoPlayer + MediaSession)
- Gradle 8.9, Android Gradle Plugin 8.7

## Contributing

Issues and pull requests are welcome. For larger changes, please open an issue first to discuss what you would like to change.

## License

No license has been chosen yet. Add a `LICENSE` file (for example MIT) to define how others may use the code.
