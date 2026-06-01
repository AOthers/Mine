# Toolbox Backup/Restore Design

## Goal

Ship a usable toolbox app named "我的" with the first tool, "备份与恢复", completed end to end.

## Home Structure

The app has three bottom tabs:

- "功能": shows available tools.
- "收藏": shows tools the user has favorited.
- "我的": lightweight app/profile information.

"备份与恢复" is the first tool card. Long-pressing the card opens a dialog:

- If not favorited, offer "收藏".
- If already favorited, offer "取消收藏".

The favorite state is local and persistent.

## Backup/Restore Tool Page

The tool page contains:

- Baidu Pan status card with "管理/去配置" action.
- "备份应用" entry.
- "恢复应用" entry.

There is no top-right settings gear on this page. Settings are reached through the status card.

## Settings

The settings page contains:

- Baidu Pan AppKey and SecretKey.
- OAuth authorization.
- Remote Baidu Pan backup path.
- Restore folder picker.
- Same-version backup strategy.

Restore folder behavior:

- Default is app external files directory under `restored_apks`.
- User can choose a folder through Android's system folder picker.
- A chosen folder is stored as a persisted SAF tree URI.
- Downloads, downloaded-APK listing, deletion, and installation handoff must work for both default file paths and SAF tree URIs.

## Backup Behavior

Backup lists installed apps, extracts selected APKs, and uploads them to Baidu Pan.

New backup filename format:

```text
<app-name>_<package-name>_<version>.apk
```

Different app versions coexist naturally. Same-version behavior:

- Overwrite by default.
- Save as copy appends `_copy-yyyyMMddHHmmss`.

Backup progress appears both in the app and in the notification shade.

## Restore Behavior

Restore reads remote backups from Baidu Pan, groups them by package/app, and supports search by:

- app name
- package name
- version

Each app opens a dialog with:

- manual download link field
- open-link action
- selectable Baidu Pan backup versions

Baidu Pan restore downloads the selected APK, saves it locally, shows notification progress, and starts Android's installer.

## Downloaded APK Manager

The restore page has a top-right download icon. It opens the downloaded APK manager.

Each downloaded APK row shows:

- APK icon
- app name
- package name
- version
- size
- modified time
- path/URI

The user can delete downloaded APKs from this dialog.

## Navigation Rules

- "收藏" or "我的" returns to "功能" on system back.
- "备份与恢复" returns to "功能".
- Backup, restore, settings, and progress pages return to "备份与恢复".
- Only the top-level "功能" page exits the app.

OAuth success returns to "备份与恢复" and refreshes backup records.

## Follow-Up Work

- Manual-link restore can become in-app APK download later.
- Backup strategy can add skip-existing-version, batch overwrite prompts, and Wi-Fi-only upload.
- Favorites can support ordering after more tools exist.
