# AppBackup

Android Kotlin / Jetpack Compose toolbox app. The product name is "我的"; the first completed tool is "备份与恢复".

## Completed Features

- Three bottom tabs: "功能", "收藏", and "我的".
- "功能" shows the "备份与恢复" tool card.
- Long-press the tool card to add or remove it from "收藏".
- "收藏" shows favorited tools and lets users open them directly.
- "备份与恢复" has backup and restore entry points.
- Baidu Pan settings are opened from the status card action "管理/去配置", not from the "我的" tab.
- Backup lists installed apps, extracts selected APKs, uploads them to Baidu Pan, and shows notification progress.
- Backup filenames use `<app-name>_<package-name>_<version>.apk`.
- Different versions are stored separately.
- Same-version behavior is configurable:
  - `OVERWRITE` by default.
  - `SAVE_AS_COPY` appends `_copy-yyyyMMddHHmmss`.
- Restore groups remote backups by app/package and lists available versions.
- Restore supports:
  - Manual link saved per package and opened in the system browser.
  - Baidu Pan download, local APK save, and Android installer handoff.
- Restore shows notification download progress.
- Restore screen has search by app name, package name, or version.
- Restore screen has a downloaded-APK manager. It shows APK icon, app name, package name, version, size, time, path, and supports delete.
- Restore save location defaults to the app external files directory under `restored_apks`.
- Users can choose a restore folder with Android's system folder picker. The selected SAF tree URI is persisted.

## Build

Use the Gradle wrapper:

```powershell
.\gradlew.bat assembleDebug
```

Command-line builds require JDK 17. The project currently pins:

```properties
org.gradle.java.home=C:/Program Files/Java/jdk-17
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Baidu Pan Setup

Create an app in the Baidu Pan developer console and configure:

- AppKey
- SecretKey
- OAuth callback: `wode://baidu.oauth`

In the app, open "备份与恢复", tap "管理/去配置", save credentials, and authorize.

Default remote backup directory:

```text
/apps/AppBackup
```

The remote backup directory is configurable in the settings page. Upload and restore listing both use the stored path.

## Code Map

- `MainActivity.kt`: page coordination, OAuth callback handling, system folder picker, browser intents, APK install intents, notification permission request, and system back handling.
- `ToolboxScreens.kt`: toolbox home, bottom tabs, favorite behavior, backup/restore home page.
- `AppListScreen.kt`: installed-app picker for backup.
- `BackupScreen.kt`: backup progress UI.
- `RestoreScreen.kt`: remote restore list, search, version dialog, downloaded-APK manager.
- `SettingsScreen.kt`: Baidu Pan credentials, authorization, remote backup path, restore folder picker, same-version strategy.
- `BackupViewModel.kt`: backup flow, restore flow, remote records, downloaded APK listing/deleting, restore folder handling, notification progress.
- `BaiduPanService.kt`: Baidu Pan OAuth, upload, list, filemetas, and download.
- `TokenStore.kt`: encrypted storage for Baidu tokens, credentials, backup path, restore folder URI/path, and strategy.
- `FavoriteStore.kt`: local favorite state.
- `RestoreLinkStore.kt`: per-package manual restore links.
- `ProgressNotifier.kt`: backup/restore notification progress.
- `RestoreTarget.kt`: output target abstraction for file paths and SAF tree downloads.

## Follow-Up Work

- Manual-link restore can be upgraded from "open in browser" to in-app APK download.
- Backup strategy can add skip-existing-version, batch overwrite prompts, and Wi-Fi-only upload.
- Favorites can later support ordering when more tools exist.
