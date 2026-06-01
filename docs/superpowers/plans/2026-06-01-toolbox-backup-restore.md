# Toolbox Backup/Restore Implementation Plan

## Objective

Complete version 1 of the "我的" toolbox app with "备份与恢复" as the first usable tool.

## Completed Scope

- Added bottom tabs: "功能", "收藏", and "我的".
- Added "备份与恢复" as the first tool card.
- Added long-press favorite/unfavorite behavior for the tool card.
- Persisted favorites with `FavoriteStore`.
- Displayed favorited tools in the "收藏" tab.
- Added backup/restore second-level page.
- Removed the top-right settings gear from the backup/restore page.
- Kept settings access through the status card action "管理/去配置".
- Added Baidu Pan credentials, authorization, and remote backup path settings.
- Added restore folder picker using Android SAF `OpenDocumentTree`.
- Persisted restore tree URI and used it for download/list/delete/install where available.
- Kept default restore directory as app external files `restored_apks`.
- Added same-version backup strategy:
  - `OVERWRITE`
  - `SAVE_AS_COPY`
- Added backup notification progress.
- Added restore download notification progress.
- Added remote restore search by app, package, and version.
- Added restore grouping by app/package and selectable versions.
- Added manual restore links stored per package and opened in the browser.
- Added Baidu Pan restore download and Android install handoff.
- Added downloaded APK manager with icon, app name, package, version, size, time, path/URI, and delete.
- Added APK metadata reading for downloaded APK rows.
- Added app name, launcher icon, and toolbox-oriented UI.
- Added system back behavior for nested screens.
- Fixed OAuth success navigation to return to "备份与恢复".
- Verified with `.\gradlew.bat assembleDebug`.

## Remaining Follow-Up

- Manual-link restore can be upgraded to in-app APK download.
- Backup can add skip-existing-version, batch overwrite prompts, and Wi-Fi-only upload.
- Favorites can add ordering when more tools are introduced.
