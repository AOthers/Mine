# Toolbox App Design Notes

## Home Structure

The app has three bottom tabs:

- `功能`: available tools.
- `收藏`: tools the user has favorited.
- `我的`: lightweight app information.

Current tool cards:

- `备份与恢复`
- `影视`

Long-pressing a card opens a favorite/unfavorite dialog. Favorite state is local and persistent.

## Backup/Restore Tool

The backup/restore page contains:

- Baidu Pan status card with a manage/configure action.
- `备份应用` entry.
- `恢复应用` entry.

There is no top-right settings gear. Settings are reached through the status card.

## Backup/Restore Settings

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
- The displayed restore folder should be readable to the user and should not misrepresent a SAF URI as a literal filesystem path.

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

When the remote backup path changes, refreshing records must clear the old list immediately and discard late results from the old path.

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

## Movie WebView

The `影视` tool opens `https://www.hhkan0.com/` inside an Android WebView.

Controls:

- Back
- Refresh
- Home
- Open in external browser

Behavior:

- JavaScript and DOM storage are enabled.
- Normal site navigation stays inside the WebView.
- Loading progress is visible.
- Load failures show retry and external-browser actions.
- Android back first goes back in WebView history, then returns to the toolbox.
- The app does not parse content, extract playback URLs, or bypass site restrictions.

## Update Check

The app checks GitHub releases on startup:

```text
https://api.github.com/repos/AOthers/Mine/releases/latest
```

Behavior:

- Compare latest release tag against `BuildConfig.VERSION_NAME`.
- Ignore a leading `v` in tags.
- If newer, show update, skip-this-update, and cancel actions.
- Update downloads the first `.apk` release asset and hands it to Android's installer.
- Skipped tags are stored locally and should not prompt again for the same tag.

## Navigation Rules

- `收藏` or `我的` returns to `功能` on system back.
- `备份与恢复` returns to `功能`.
- Backup, restore, settings, and progress pages return to `备份与恢复`.
- `影视` uses WebView history first, then returns to `功能`.
- Only the top-level `功能` page exits the app.

OAuth success returns to `备份与恢复` and refreshes backup records.
