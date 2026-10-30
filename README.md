# Radiant

A simple, native Android music player for your **local audio files**. It plays music only from the one folder you choose, so ringtones, voice messages and other sounds on your phone never end up in your library.

No ads, no accounts, no internet access required. Everything stays on your device.

> **Note:** the app interface is currently in Russian only.

## Features

- **Your music, your folder.** Pick a folder once; Radiant scans it and its subfolders and rescans every time you open the app. It uses Android's Storage Access Framework, so it cannot see anything outside that folder.
- **Library:** Playlists, Albums (grouped automatically from file tags) and Tracks, with search and sorting in every section.
- **Playlists:** create, rename, delete, add and remove tracks. *Liked tracks* is always pinned at the top.
- **Queues:** a fourth section with exactly four fixed queues (they can't be created or deleted). Add tracks from the player button or the "⋮" menu, remove them, and reorder by long-press drag or "move up / down".
- **Drag to reorder** playlists, albums and tracks with a long press.
- **Background playback** with a media notification, lock screen controls and headset button support.
- **Player:** play/pause, next/previous, seek, shuffle, repeat (off / all / one), cover art, and a quality badge for the file format and bitrate.
- **Lyrics:** a button under the "i" in the player opens the lyrics. Radiant looks for lyrics in the file tags (ID3 USLT/SYLT, Vorbis `LYRICS`, M4A `©lyr`) and in `.lrc` / `.txt` files with the same name next to the track, and checks again every time you open the app. Synced (`.lrc`) lyrics scroll along with the song, highlight the current line and fade the lines already sung; tap a line to jump to it. Plain lyrics are shown in a compact view.
- **Playback speed** from 3× slower to 3× faster in 0.1 steps, with pitch preserved.
- **Sound settings:** mono/stereo, volume normalization, bass and treble boost, subsonic filter, and a 12-band equalizer with 10 presets. The equalizer can apply one curve to all tracks or remember a separate curve for each track.
- **Themes:** light, true-black dark (great for OLED screens) or system default, with dynamic colors on Android 12+.
- **Track details:** tags, format, bitrate, sample rate, channels, file size and more.
- **Formats:** MP3, FLAC, OGG/Opus, M4A/AAC, WAV and anything else Android can decode.

## Requirements

- Android 8.0 (API 26) or newer

## Installation

The latest stable version is always available on the **[Releases](../../releases/latest)** page as an `.apk` file.

1. Download the `.apk` from the latest release to your phone.
2. Open it and allow installation from unknown sources if Android asks.
3. Launch Radiant.

New versions install over old ones without uninstalling, so your playlists and settings are kept.

## Getting started

1. Open the app and tap **Выбрать папку** (*Choose folder*).
2. Select the folder with your music. The library fills automatically.
3. Browse **Плейлисты** (*Playlists*), **Альбомы** (*Albums*) and **Треки** (*Tracks*) and tap a track to play it.
4. To build a playlist, tap **⋮** next to any track and choose **Добавить в плейлист** (*Add to playlist*).

## Building from source

You need JDK 17 and either Android Studio or the Android SDK with Gradle 8.9+.

- **Android Studio:** open the project, switch the build variant to **release** (debug builds are noticeably slower), then choose *Build → Build APK(s)*.
- **Command line:** run `gradle assembleRelease`.
- **GitHub Actions:** fork the repository and run the **Build APK** workflow; the APK is attached to the run as an artifact.

The APK is written to `app/build/outputs/apk/release/app-release.apk`.

> **Signing.** Official releases are signed with a private key that is not part of this repository. A build you make yourself is signed with the standard Android debug key instead, so it installs fine but will not update over an official release (uninstall one before installing the other). If you want your own signed builds in GitHub Actions, add the repository secrets `KEYSTORE_BASE64` (your `.jks` file encoded in base64) and `KEYSTORE_PASSWORD`; the key alias defaults to `radiant`.

## Built with

Kotlin, Jetpack Compose (Material 3) and AndroidX Media3 (ExoPlayer).

## Contributing

Issues and pull requests are welcome. For larger changes, please open an issue first to discuss what you would like to change. By submitting a pull request you agree that your contribution is provided under the same license as the project.

## License

Radiant is licensed under the [PolyForm Noncommercial License 1.0.0](LICENSE).

You are free to use, study, modify and share it for any **noncommercial** purpose, including personal use, hobby projects, education and non-profit organizations. **Commercial use is not permitted** without a separate agreement, so you may not sell the app or publish it for profit. If you would like to use Radiant commercially, please get in touch by opening an issue in this repository.

Please keep the `LICENSE` file and its `Required Notice` line with any copy you share.
