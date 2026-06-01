# Toolbox App Implementation Summary

## Current Scope

Version `1.1` includes three main product areas:

- Backup and restore through Baidu Pan.
- Embedded movie-site browsing through WebView.
- Startup update checks through GitHub releases.

## Completed Backup/Restore Scope

- Added bottom tabs: `功能`, `收藏`, and `我的`.
- Added the backup/restore tool card.
- Added long-press favorite/unfavorite behavior with `FavoriteStore`.
- Added backup/restore second-level page.
- Added Baidu Pan credential, authorization, and remote backup path settings.
- Added restore folder picker using Android SAF `OpenDocumentTree`.
- Persisted restore tree URI and used it for download, list, delete, and install handoff where available.
- Kept default restore directory as app external files `restored_apks`.
- Added same-version backup strategy:
  - `OVERWRITE`
  - `SAVE_AS_COPY`
- Added backup and restore download notification progress.
- Added remote restore search by app, package, and version.
- Added restore grouping by app/package and selectable versions.
- Added manual restore links stored per package and opened in the browser.
- Added Baidu Pan restore download and Android install handoff.
- Added downloaded APK manager with icon, app name, package, version, size, time, path/URI, and delete.
- Added APK metadata reading for downloaded APK rows.
- Fixed backup-record refresh so changing the remote path clears stale records and ignores outdated loads.
- Added readable restore-folder display based on the selected default path or SAF tree URI.

## Completed Movie Scope

- Added a `影视` tool card and favorite support.
- Added `MovieWebScreen.kt`.
- Embedded `https://www.hhkan0.com/` in a WebView.
- Added back, refresh, home, external-browser, loading, retry, and load-error behavior.
- Kept site links inside the WebView.
- Integrated system back: WebView history first, then return to the toolbox.
- Avoided playback parsing, source extraction, scraping, or restriction bypass.

## Completed Update Scope

- Added `UpdateInfo.kt` and `UpdateService.kt`.
- Checks `https://api.github.com/repos/AOthers/Mine/releases/latest` on startup.
- Compares latest release tag with `BuildConfig.VERSION_NAME`, ignoring a leading `v`.
- Shows an update dialog with update, skip-this-version, and cancel actions.
- Downloads the first `.apk` release asset and opens Android's installer.
- Stores skipped release tags locally so skipped updates do not repeat for the same version.

## Verification

Use:

```powershell
.\gradlew.bat assembleDebug
```

Manual checks:

- Toolbox shows backup/restore and movie cards.
- Long-press favorite works for both tools and the cards appear in `收藏`.
- Backup records refresh after changing the Baidu Pan directory.
- Restore folder display is readable after choosing a folder.
- Movie WebView loads, navigates internally, refreshes, returns home, opens externally, and handles back.
- Startup update dialog appears only when GitHub latest release is newer than the installed version.

## Follow-Up

- Manual-link restore can be upgraded to in-app APK download.
- Backup can add skip-existing-version, batch overwrite prompts, and Wi-Fi-only upload.
- Favorites can add ordering after more tools exist.
- Update flow can add download progress UI if release APKs become large.
