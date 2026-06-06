# Agent Notes

## Project

Project root: `C:\Users\Aother\Desktop\我的\AppBackup`.

This is an Android Kotlin / Jetpack Compose toolbox app named `我的`. Current app version is `v1.4`.

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

- `MainActivity.kt` coordinates screens and Activity-only integrations: OAuth callback, SAF folder picker, browser intents, APK installer intents, notification permission, update prompts, WebView back handling, system back, and orientation-change continuity.
- `ToolboxScreens.kt` owns the toolbox shell, bottom tabs, favorite UI, feature cards, and backup/restore entry page.
- `MovieWebScreen.kt` embeds the movie WebView with toolbar navigation, progress, retry, source switching, external browser handoff, in-WebView site navigation, and WebChromeClient fullscreen custom-view handling.
- `MusicScreen.kt` owns the music player UI, source/folder dialogs, sorting dialog, lyric strip, track list, and playback controls.
- `ReaderScreens.kt` owns the reader bookshelf, text/EPUB reader, comic reader, PDF reader, and reader-level back handling.
- `AppListScreen.kt` is the installed-app picker for backup.
- `BackupScreen.kt` shows backup progress.
- `RestoreScreen.kt` owns remote restore search/listing, version selection, manual link dialog, and downloaded APK management.
- `SettingsScreen.kt` owns Baidu Pan credentials, authorization, remote backup path, restore folder picker, and same-version strategy.
- `BackupViewModel.kt` owns backup/restore workflows, remote backup records, local restored APK records, delete behavior, notification progress, and restore target resolution.
- `MusicViewModel.kt` owns music scan state, playback state, Media3 session integration, lyric lookup, sort mode, source management, and folder cache refresh decisions.
- `ReaderViewModel.kt` owns reader imports, library state, selected item loading, progress updates, PDF paging, and reading settings.
- `BaiduPanService.kt` owns Baidu Pan OAuth, upload, list, filemetas, and download calls.
- `UpdateService.kt` checks GitHub releases for app updates and downloads release APK assets. Update checks are manual from the `我的` tab; do not re-add startup update prompts unless the dialog lifecycle issue is explicitly redesigned.
- `MusicLibraryService.kt` discovers music through Android `MediaStore` and SAF folder traversal.
- `ReaderImportService.kt` imports SAF files/folders into the local reader library.
- `ReaderLibraryStore.kt` persists reader library entries, progress, and reading settings.
- `ReaderFormat.kt` classifies supported reader formats, validates archive entries, and applies natural comic image ordering.
- `TextReaderService.kt`, `EpubReaderService.kt`, `PdfReaderService.kt`, and `ComicReaderService.kt` load local reader content.
- `MovieSourceStore.kt` stores movie site sources, the current source, and the default source `https://www.hhkan0.com/`.
- `MusicStore.kt` stores selected music sources, system-source state, sort mode, and folder scan cache.
- `OnlineLyricsService.kt` searches LRCLIB first, then OIAPI QQ Music as a fallback for synced or plain lyrics. Online lyrics are not cached by default.
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
- Movie sources are configurable through `MovieSourceStore`. Adding a source only saves it to the list; clicking a source switches the current WebView source. Custom sources can be deleted, but the default source must remain restorable.
- Movie WebView fullscreen depends on `WebChromeClient.onShowCustomView/onHideCustomView`; do not replace it with only `WebViewClient`.
- `MainActivity` must keep `android:configChanges="keyboardHidden|orientation|screenSize"` so rotating while watching movies does not rebuild the Activity and return to the tools page.
- Music source management uses bottom-sheet style dialogs in `MusicScreen`; `MainActivity` only owns runtime permissions and SAF folder picker contracts.
- Music list sorting is persisted in `MusicStore`. Reopening the music screen in the same process should reuse the existing library; app restart or explicit refresh/source changes may rescan.
- Music folder scans should reuse cached tracks where possible. Remove stale folder cache entries when a folder source is removed.
- Music playback should keep Media3 `MediaSession` attached to the app's ExoPlayer and set `MediaMetadata` for each queue item so Android system media controls can show playback state and track info.
- Music title/artist cleanup should prefer known artists over string length guesses, correct obvious reversed MediaStore title/artist tags, and bump the folder track cache version when parser output changes.
- Lyric lookup order is nearby `.lrc`, embedded audio metadata, LRCLIB online search, then OIAPI QQ Music fallback search. Skip blank, `null`, or bracket-only lyric lines so the strip does not flicker back to "no lyrics".
- The music lyric strip has a fixed height. Long current lines should use marquee scrolling instead of resizing the header area.
- Reader v1 is local-only. Do not add online novel/comic sources, scraping, OCR, cloud sync, or protected-content bypassing without a new product decision.
- Reader v1 supports `.txt`, `.epub`, `.pdf`, image folders, `.zip`, and `.cbz`; images inside comic folders/archives support `.jpg`, `.jpeg`, `.png`, `.webp`, and `.gif`.
- `.mobi`, `.azw3`, `.rar`, `.cbr`, and `.7z` are future-extension formats, not supported in the first reader version.
- Reader imports must keep SAF URI permissions and use `DocumentFile`/`ContentResolver`; do not convert SAF URIs into fake filesystem paths.
- Comic archives must reject unsafe zip entries such as absolute paths or `..` segments before extracting to cache.
- Update checking compares the installed `BuildConfig.VERSION_NAME` with the latest GitHub release tag from `AOthers/Mine`. Release tags may use a leading `v`; the app strips it for comparison.
- Update downloads use the first `.apk` asset from the latest GitHub release and then hand off to Android's package installer.
- Manual update checks live in the `我的` tab, ignore any skipped release tag so users can retry a skipped update, and show APK download progress with cancel support.

## Navigation

- System back should navigate in-app before exiting.
- Child backup/restore pages return to backup/restore.
- Top-level non-tools tabs return to `功能`.
- Movie WebView back first navigates WebView history; if there is no WebView history, it returns to the toolbox.
- Reader back first closes the currently opened book/comic/PDF to the bookshelf; bookshelf back returns to the toolbox.
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
