# 我的

`我的` is an Android Kotlin / Jetpack Compose toolbox app. Current version: `1.2`.

## Features

- Toolbox home with three tabs: `功能`, `收藏`, and `我的`.
- Long-press tool cards to favorite or unfavorite them.
- Backup/restore tool for exporting installed APKs, backing them up to Baidu Pan, downloading backups, and installing restored APKs.
- Movie tool that embeds `https://www.hhkan0.com/` in a WebView for user-initiated browsing.
- Music player that scans system music or selected folders, supports local/online lyrics, and offers repeat/shuffle modes.
- Startup and manual update checks against [AOthers/Mine GitHub releases](https://github.com/AOthers/Mine/releases).

## Backup And Restore

The backup/restore tool supports:

- Listing installed apps and extracting selected APKs.
- Uploading APK backups to a configurable Baidu Pan directory.
- Backup filenames in this format:

```text
<app-name>_<package-name>_<version>.apk
```

- Compatibility with older backup filenames:

```text
<package-name>_<version>.apk
```

- Same-version handling:
  - `OVERWRITE`
  - `SAVE_AS_COPY`, appending `_copy-yyyyMMddHHmmss` before `.apk`
- Remote restore search by app name, package name, and version.
- Downloaded APK management with icon, app name, package, version, size, modified time, and path/URI.
- Default restore downloads under the app external files `restored_apks` directory.
- Optional restore download folder selection through Android SAF.
- Notification progress for backup and restore downloads.

## Movie WebView

The movie tool opens `https://www.hhkan0.com/` inside the app.

It provides:

- Back, refresh, home, and external-browser actions.
- Loading progress.
- In-app handling for site navigation.
- Retry and browser-open actions on load failure.

The app does not parse playback sources, bypass site restrictions, or scrape protected content.

## Music Player

The music tool supports:

- One-tap system music recognition through Android `MediaStore`.
- User-selected music folders through Android SAF, with folder add/remove management.
- Fast folder rescans by caching recognized tracks and only rescanning when needed.
- Sorting by name or modified time, in ascending or descending order.
- Playback modes: repeat one, repeat list, and shuffle.
- Lyric lookup from nearby `.lrc` files, embedded audio metadata, and online LRCLIB search.
- A fixed-height lyric strip with marquee scrolling for long current lines.

Online lyrics are looked up on demand and are not cached by default.

## Updates

On app startup, and when the user taps `检查更新` in the `我的` tab, the app checks:

```text
https://api.github.com/repos/AOthers/Mine/releases/latest
```

If the latest release tag is newer than `BuildConfig.VERSION_NAME`, the app shows an update dialog with:

- Update
- Skip this update
- Cancel

The update action downloads the first `.apk` asset from the release and hands it to Android's installer.

Manual update checks ignore the "skip this update" choice so the user can retry a skipped release.

## Baidu Pan Setup

Before using Baidu Pan backup/restore, create an app in the Baidu Pan open platform and configure:

- AppKey
- SecretKey

OAuth callback:

```text
wode://baidu.oauth
```

Default remote backup directory:

```text
/apps/AppBackup
```

The remote directory can be changed in the backup/restore settings page.

## Build

Requirements:

- Android Studio
- JDK 17
- Android Gradle Plugin
- Kotlin
- Jetpack Compose

`gradle.properties` currently pins JDK 17:

```properties
org.gradle.java.home=C:/Program Files/Java/jdk-17
```

Build debug APK:

```powershell
.\gradlew.bat assembleDebug
```

Output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Release Build

Release signing is configured through local files:

```text
mine-release.jks
keystore.properties
```

`keystore.properties` example:

```properties
storeFile=mine-release.jks
storePassword=your-store-password
keyAlias=mine
keyPassword=your-key-password
```

Build release APK:

```powershell
.\gradlew.bat assembleRelease
```

Output:

```text
app/build/outputs/apk/release/app-release.apk
```

## Code Map

- `MainActivity.kt`: screen coordination, Activity integrations, update dialog, installer handoff, and system back.
- `ToolboxScreens.kt`: toolbox tabs, tool cards, favorites, and app info.
- `MovieWebScreen.kt`: embedded movie WebView.
- `MusicScreen.kt`: music player UI, source/folder dialogs, sorting, lyrics, and controls.
- `SettingsScreen.kt`: Baidu Pan settings, backup path, restore folder, and same-version strategy.
- `BackupViewModel.kt`: backup/restore workflows and local/remote APK state.
- `MusicViewModel.kt`: music library scans, playback state, lyric lookup, sort state, and source management.
- `MusicLibraryService.kt`: `MediaStore` and SAF folder music discovery.
- `MusicStore.kt`: persisted music sources, sort mode, and folder track cache.
- `OnlineLyricsService.kt`: LRCLIB lyric search and synced/plain lyric retrieval.
- `BaiduPanService.kt`: Baidu Pan OAuth and file APIs.
- `UpdateService.kt`: GitHub release check and APK download.
- `FavoriteStore.kt`: persisted tool favorites.
- `TokenStore.kt`: persisted credentials, tokens, paths, and restore strategy.

## License

No license has been specified yet. Add a `LICENSE` file before publishing a formal open-source release.
