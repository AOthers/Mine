# Agent Notes

## Project

Project root: `C:\Users\Aother\Desktop\我的\AppBackup`.

This is an Android Kotlin / Jetpack Compose toolbox app named `我的`. Current app version is `1.2`.

## Build

Use JDK 17 and run:

```powershell
.\gradlew.bat assembleDebug
```

`gradle.properties` pins:

```properties
org.gradle.java.home=C:/Program Files/Java/jdk-17
```

Do not commit generated outputs from `.gradle/`, `app/build/`, `build/`, or machine-local `local.properties`.

## Before Coding

Before starting any non-trivial feature, bug fix, release, WebView, SAF/storage, Baidu Pan, or update-check work, read `docs/development-lessons.md` first. It records project-specific pitfalls that have already caused bugs: Compose runtime crashes, stale backup records after path changes, SAF path display, GitHub Release APK assets, debug/release APK size differences, Gradle wrapper network restrictions, and unrelated `.idea` changes.

## Architecture

- `MainActivity.kt` coordinates screens and Activity-only integrations: OAuth callback, SAF folder picker, browser intents, APK installer intents, notification permission, update prompts, WebView back handling, and system back.
- `ToolboxScreens.kt` owns the toolbox shell, bottom tabs, favorite UI, feature cards, and backup/restore entry page.
- `MovieWebScreen.kt` embeds the movie site WebView at `https://www.hhkan0.com/` with toolbar navigation, progress, retry, external browser handoff, and in-WebView site navigation.
- `MusicScreen.kt` owns the music player UI, source/folder dialogs, sorting dialog, lyric strip, track list, and playback controls.
- `AppListScreen.kt` is the installed-app picker for backup.
- `BackupScreen.kt` shows backup progress.
- `RestoreScreen.kt` owns remote restore search/listing, version selection, manual link dialog, and downloaded APK management.
- `SettingsScreen.kt` owns Baidu Pan credentials, authorization, remote backup path, restore folder picker, and same-version strategy.
- `BackupViewModel.kt` owns backup/restore workflows, remote backup records, local restored APK records, delete behavior, notification progress, and restore target resolution.
- `MusicViewModel.kt` owns music scan state, playback state, lyric lookup, sort mode, source management, and folder cache refresh decisions.
- `BaiduPanService.kt` owns Baidu Pan OAuth, upload, list, filemetas, and download calls.
- `UpdateService.kt` checks GitHub releases for app updates and downloads release APK assets.
- `MusicLibraryService.kt` discovers music through Android `MediaStore` and SAF folder traversal.
- `MusicStore.kt` stores selected music sources, system-source state, sort mode, and folder scan cache.
- `OnlineLyricsService.kt` searches LRCLIB for synced or plain lyrics. Online lyrics are not cached by default.
- `TokenStore.kt` stores tokens, credentials, remote backup path, restore folder URI/path, and same-version strategy.
- `FavoriteStore.kt` stores local tool favorites.
- `RestoreLinkStore.kt` stores manual restore links by package.
- `ProgressNotifier.kt` handles notification progress.
- `RestoreTarget.kt` abstracts either a regular file output or SAF tree output.

## Product Rules

- Baidu Pan settings stay inside the backup/restore settings page, not in the `我的` tab.
- Long-press a tool card to favorite/unfavorite it. Favorited tools appear in `收藏`.
- The backup/restore page has no top-right settings gear. Settings are opened through the status card action.
- Installed-app list loading should happen when the user opens backup/restore, not when Baidu Pan is configured.
- Remote backup path must use `tokenStore.getBackupPath()` for both upload and remote listing. Refreshing backup records must clear stale records and discard results from an old path if the path changes mid-load.
- Restore location defaults to app external files `restored_apks`.
- If the user chooses a folder through Android's folder picker, save the tree URI and use `DocumentFile`/content resolver for download, list, delete, and install handoff.
- Restore folder display should show a user-readable decoded path; do not claim a SAF path is a real filesystem path unless it really is one.
- OAuth success should return to backup/restore and refresh backup records.
- Movie WebView v1 is browsing only: do not parse site structure, extract playback URLs, bypass restrictions, or scrape copyrighted content.
- Music source management uses bottom-sheet style dialogs in `MusicScreen`; `MainActivity` only owns runtime permissions and SAF folder picker contracts.
- Music list sorting is persisted in `MusicStore`. Reopening the music screen in the same process should reuse the existing library; app restart or explicit refresh/source changes may rescan.
- Music folder scans should reuse cached tracks where possible. Remove stale folder cache entries when a folder source is removed.
- Lyric lookup order is nearby `.lrc`, embedded audio metadata, then LRCLIB online search. Skip blank, `null`, or bracket-only lyric lines so the strip does not flicker back to "no lyrics".
- The music lyric strip has a fixed height. Long current lines should use marquee scrolling instead of resizing the header area.
- Update checking compares the installed `BuildConfig.VERSION_NAME` with the latest GitHub release tag from `AOthers/Mine`. Release tags may use a leading `v`; the app strips it for comparison.
- Update downloads use the first `.apk` asset from the latest GitHub release and then hand off to Android's package installer.
- Manual update checks live in the `我的` tab and ignore any skipped release tag so users can retry a skipped update.

## Navigation

- System back should navigate in-app before exiting.
- Child backup/restore pages return to backup/restore.
- Top-level non-tools tabs return to `功能`.
- Movie WebView back first navigates WebView history; if there is no WebView history, it returns to the toolbox.
- Only the main tools page exits the app.

## Backup Records

New backup filenames use:

```text
<app-name>_<package-name>_<version>.apk
```

Keep fallback compatibility for old backups:

```text
<package-name>_<version>.apk
```

Same-version strategy is stored in `TokenStore.SameVersionStrategy`.

- Default: `OVERWRITE`.
- Alternative: `SAVE_AS_COPY`, appending `_copy-yyyyMMddHHmmss` before `.apk`.

## Verification

Before handing off feature changes, run:

```powershell
.\gradlew.bat assembleDebug
```
