# Agent Notes

## Project

Project root: `C:\Users\Aother\Desktop\我的\AppBackup`.

This is an Android Kotlin / Jetpack Compose app. The app name is "我的". Product-wise it is a toolbox; the first completed tool is "备份与恢复".

## Build

Use:

```powershell
.\gradlew.bat assembleDebug
```

Command-line builds should use JDK 17. `gradle.properties` pins:

```properties
org.gradle.java.home=C:/Program Files/Java/jdk-17
```

Do not commit generated outputs from `.gradle/`, `app/build/`, `build/`, or machine-local `local.properties`.

## Architecture

- `MainActivity.kt` coordinates screens and Activity-only integrations: OAuth callback, SAF folder picker, browser intents, APK installer intents, notification permission, and system back.
- `ToolboxScreens.kt` owns the toolbox shell, bottom tabs, favorite UI, feature card, and backup/restore entry page.
- `AppListScreen.kt` is the installed-app picker for backup.
- `BackupScreen.kt` shows backup progress.
- `RestoreScreen.kt` owns remote restore search/listing, version selection, manual link dialog, and downloaded APK management.
- `SettingsScreen.kt` owns Baidu Pan credentials, authorization, remote backup path, restore folder picker, and same-version strategy.
- `BackupViewModel.kt` owns backup/restore workflows, remote backup records, local restored APK records, delete behavior, notification progress, and restore target resolution.
- `BaiduPanService.kt` owns Baidu Pan OAuth, upload, list, filemetas, and download calls.
- `TokenStore.kt` stores tokens, credentials, remote backup path, restore folder URI/path, and same-version strategy.
- `FavoriteStore.kt` stores local tool favorites.
- `RestoreLinkStore.kt` stores manual restore links by package.
- `ProgressNotifier.kt` handles notification progress.
- `RestoreTarget.kt` abstracts either a regular file output or SAF tree output.

## Product Rules

- Baidu Pan settings do not belong in the "我的" tab.
- The "备份与恢复" page no longer has a top-right gear. Settings are opened through the status card action "管理/去配置".
- Long-press a tool card to favorite/unfavorite it. Favorited tools appear in "收藏".
- Remote backup path must use `tokenStore.getBackupPath()` for both upload and remote listing.
- Restore location defaults to app external files `restored_apks`.
- If the user chooses a folder through Android's folder picker, save the tree URI and use `DocumentFile`/content resolver for download, list, delete, and install handoff.
- OAuth success should return to "备份与恢复" and refresh backup records.
- System back should navigate in-app before exiting: child pages return to "备份与恢复"; top-level non-tools tabs return to "功能"; only the main tools page exits.
- Restore rows should show readable app names. Prefer backup filename metadata for remote rows and APK archive metadata for downloaded APK rows.

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
