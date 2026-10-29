# Local Player

A simple, native Android music player for your **local audio files**. It only plays music from a single folder that you choose in the settings, so ringtones, voice messages, and other sounds on your phone never end up in your library.

Built with Kotlin, Jetpack Compose, and Media3 (ExoPlayer). No ads, no accounts, no internet access required.

## Features

- **Single music folder.** Pick a folder in Settings; the app scans it and its subfolders. It uses Android's Storage Access Framework, so it never requests access to your whole storage and cannot see anything outside the folder you picked.
- **Three library sections:**
  - **Playlists**: playlists you create yourself (create, rename, delete, add and remove tracks). The default playlist **Liked tracks** (*Понравившиеся треки*) is always pinned at the top and cannot be renamed or deleted
  - **Albums**: grouped automatically from the files' tags; an album screen shows its cover art at the top
  - **Tracks**: all tracks in one mixed list
- **Background playback** with a media notification, lock screen controls, and headset button support.
- **Player controls:** play/pause, next/previous, seek, shuffle, repeat (off / all / one).
- **Cover art** read from embedded tags and cached on disk in a space-saving way (small WebP thumbnails, one copy per album, no size limit, cleaned of removed tracks), so covers load instantly after restarting the app.
- **Settings footer:** *Clear app cache* (two taps to confirm, shows the current cache size) and *Reset to factory settings* (confirmation dialog: reset only settings, or settings and cache). The reset restores sound settings, all equalizers and sorting; playlists and the chosen folder are kept.
- **Search** (magnifier left of the gear): finds playlists, albums or tracks depending on the open tab. Partial and whole-word matches, case-insensitive, `ё`=`е`; several words must all match.
- **Heart and info buttons** next to the track title in the player: the heart works like the "+" in Spotify and opens a compact window with your playlists. *Liked tracks* are pinned at the top (with a divider below), the other playlists follow in the same order as in the **Playlists** tab. Tap a playlist to add the track or remove it again; a check mark shows where the track already is, and the heart is filled when the track is in at least one playlist. The round **i** button opens a window with all track details (tags, format, bitrate, sample rate, channels, size, file date, date added to the player, cover size; the folder path is not shown).
- **Theme** (Settings): *Light*, *Dark* (true black, for OLED screens) or *System* (follows the phone's dark mode; the default).
- **Quality badge** above the cover in the player: a rounded pill with the file format and quality (e.g. `MP3 / 192 kbps`, `FLAC / 24 Bit – 96 kHz`), colored by "rarity" like in games: light blue → green → blue (lossy), purple (CD-quality lossless), red (hi-res), gold (24 Bit / 176.4 kHz and above).
- **Always up to date:** the folder is rescanned every time you open the app (or return to it), so added and deleted files show up automatically. Track metadata is cached, so rescans are fast.
- **Sound settings:**
  - **Mono / stereo** switch
  - **Volume normalization** that evens out loudness between tracks (a slow automatic gain control with a soft limiter, applied in real time)
  - **Bass boost** and **treble boost** from 0 to +15 dB, and a **subsonic filter** (intensity 0–100 %), adjustable while a song is playing. In the player they are a row of buttons (Bass / Treble / Subsonic / EQ) that open the same sliders as in settings
  - **12-band equalizer** (32 Hz to 16 kHz, ±12 dB) with two modes:
    - *All tracks*: one curve applies to every track; it can be edited both in Settings and in the player
    - *Per track*: every track remembers its own curve permanently (stored on the device); in this mode the curves can be edited only in the player, and the Settings sliders are locked
    - **10 presets** (Rock, Pop, Hip-hop, Electronic, Jazz, Classical, Metal, Acoustic, Vocal, Bass) in both Settings and the player's EQ screen (in *Per track* mode: in the player, applied to the current track)
    - The mode is chosen in Settings only; the player's equalizer screen has no mode switch and explains which mode is active
  - Bass, treble, subsonic filter and the equalizer are independent and can be used together. Switching equalizer modes never erases saved curves.
- **Playback speed** from −3x (3 times slower) to 3x (3 times faster) in 0.1 steps (the **Speed** button in the player's sound row, also in Settings). Pitch is preserved; the current speed is shown on the button while it is on.
- **Sorting** in Playlists, Albums and Tracks: a thin bar under the title with the sort type on the left (by name, artist, album, duration, file date, creation/change date, number of tracks, or *Manual*) and ascending/descending on the right. The choice is remembered per section.
- **Reordering by long press:** hold a playlist, album or track and drag it up or down (the list scrolls near the edges). The list then switches to *Manual* sorting and the order is saved. Tracks inside a playlist can be reordered the same way.
- **Smooth animations** for screen transitions, tab switching, the full-screen player, and list changes.
- Supported formats: MP3, FLAC, OGG/Opus, M4A/AAC, WAV, and other formats supported by Android.
- Material 3 design with dynamic colors on Android 12+ and a light / true-black dark / system theme.

## Requirements

- Android 8.0 (API 26) or newer

## Download and install

Build the APK yourself using one of the methods below, then copy `app-release.apk` to your phone, open it, and allow installation from unknown sources when prompted.

## Building from source

### Option 1: Android Studio

1. Install [Android Studio](https://developer.android.com/studio). It bundles the JDK and the Android SDK.
2. Clone this repository and open the project folder (**File → Open**).
3. Wait for the Gradle sync to finish (the first sync downloads dependencies and can take several minutes).
4. Open **Build → Select Build Variant** and switch `app` to **release**. This matters: debug builds are noticeably slower and animations will look choppy.
5. Choose **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
6. The APK will be at `app/build/outputs/apk/release/app-release.apk`.

You can also run the app directly on a connected device or an emulator with the **Run** button (with the **release** variant selected for real-world performance).

### Option 2: Command line

You need JDK 17 and the Android SDK (set `ANDROID_HOME`), plus Gradle 8.9 or newer:

```bash
gradle assembleRelease
```

The APK will be at `app/build/outputs/apk/release/app-release.apk`.

### Option 3: GitHub Actions (no local setup)

This repository includes a workflow in `.github/workflows/build.yml`.

1. Fork this repository or push it to your own GitHub account.
2. Open the **Actions** tab, select **Build APK**, and click **Run workflow** (it also runs on every push to `main`/`master`).
3. When the run finishes, download the **LocalPlayer-release-apk** artifact. It contains `app-release.apk`.

> **Signing.** Builds are signed with the keystore committed in `app/localplayer.jks`, so new versions install over old ones without uninstalling (your playlists are kept). This key is public, so it is only suitable for personal use. If you want to distribute the app, generate your own keystore, keep it private, and update the `signingConfigs` block in `app/build.gradle.kts`.

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
├── Ui.kt                  # All screens and animations (Compose)
├── ListTools.kt           # Long-press drag reordering and the sort bar
├── PlayerViewModel.kt     # App state, playlists, player control
├── Library.kt             # Folder scanning, metadata cache, playlist storage
├── PlaybackService.kt     # Background playback (Media3 MediaSessionService)
├── AudioFx.kt             # Sound settings storage + real-time audio processor (mono, normalization, 12-band EQ, bass/treble shelves, subsonic filter)
├── CoverStore.kt          # Disk cache of cover thumbnails
├── Search.kt              # Query matching for the search field
├── EqPresets.kt           # Equalizer presets
├── TrackInfo.kt           # Data for the "i" track details window
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
