# Music Player Tool Design

## Goal

Add a local music player tool to the existing toolbox app. The current version discovers music from the device's system media library, allows the user to add custom music folders, plays common audio formats, supports local and online lyrics, sorts the music list, and shows lyrics fixed at the top of the in-app music page.

Global floating lyrics are out of scope for this version and should be kept as a follow-up plan.

## Entry Points

The toolbox home adds a third tool card:

- Title: `音乐`
- Subtitle: local music playback with lyrics
- Status: shows `已收藏` when favorited, otherwise a short library status such as `本地音乐`
- Icon: Material music icon

The favorites tab supports the music tool with the same long-press favorite/unfavorite behavior used by the backup/restore and movie tools.

Selecting the card opens a new music player screen. Android system back returns from the music screen to the top-level tools tab.

## Music Sources

The player reads music from two source types.

System media library:

- Query Android `MediaStore.Audio.Media`.
- Include only rows that represent playable music.
- Request the runtime audio read permission needed for the current Android version before querying.
- On Android 13 and newer, use `READ_MEDIA_AUDIO`.
- On older supported versions, use `READ_EXTERNAL_STORAGE` if required by the platform behavior.

User-selected folders:

- Let the user add folders through Android's system folder picker.
- Persist tree URI permissions with SAF.
- Store selected folder URIs locally.
- Scan each selected tree for supported audio files and sibling lyric files.
- Cache recognized folder tracks so reopening the music page in the same process does not force another slow tree walk.
- Let the user remove a selected folder from the music source list without deleting files.

The combined library should deduplicate tracks by URI where possible. A refresh action reloads both system media and selected folder sources.

## Supported Audio Formats

Playback should use AndroidX Media3 ExoPlayer instead of manually controlling `MediaPlayer`.

Expected first-version support includes common formats handled by the platform and Media3 extractors:

- MP3
- M4A/AAC
- FLAC
- OGG/Opus
- WAV

Unsupported or unreadable files should be skipped during scan or shown with a non-blocking error when playback fails.

## Data Model

Use a focused music model separate from backup/restore state:

- Track id
- Display title
- Artist
- Album, when available
- Duration
- Modified time, when available
- Content URI
- Source type: system library or custom folder
- Folder display name, when applicable
- Optional lyric URI

Music preferences should be stored separately from Baidu Pan and restore settings. Store selected folder tree URIs, whether the system library is enabled, the list sort mode, and cached folder scan results.

## Lyrics

Lyric lookup order:

1. Find a sibling `.lrc` file with the same base filename as the audio file.
2. If no `.lrc` file is found, try to read embedded lyric metadata from the audio file.
3. If local lookup fails, search LRCLIB online by title, artist, and duration.

`.lrc` behavior:

- Parse timestamped lines.
- Support multiple timestamps on one line.
- Sort lines by timestamp.
- During playback, compute the current line from ExoPlayer's playback position.
- Show the current line prominently and the next line as secondary text when available.

Embedded lyric behavior:

- Treat synchronized embedded lyrics as timed lyrics if timestamps can be parsed.
- Treat unsynchronized embedded lyrics as plain text.
- For plain text lyrics, show the available lyric text without attempting line-by-line sync.

If no lyrics exist, the fixed lyric area shows a calm empty state such as `暂无歌词`.

Online lyric search is on demand and not cached by default. Blank, `null`, and bracket-only lyric lines should be ignored so the lyric strip does not flicker back to the empty state between timed lines.

## Music Screen UI

The page uses a normal app screen, not a landing page.

Top area:

- A top app bar with back navigation, title `音乐`, refresh action, sort action, folder-management action, and source action.
- A fixed lyric strip immediately below the app bar.
- The lyric strip remains at the top of the music page while the track list scrolls.
- The lyric strip has a fixed height. Long current lines use marquee scrolling instead of resizing the page.

Playback area:

- Current track title and artist.
- Playback controls: previous, play/pause, next.
- Playback mode control: repeat one, repeat list, and shuffle.
- A progress slider with elapsed and total duration.
- A small error or empty state when the current track cannot be played.

Library area:

- Scrollable track list.
- Track rows show title, artist, duration, and source marker.
- Sort options: name ascending/descending and modified time ascending/descending.
- Tapping a track starts playback.
- The currently playing track is visually distinguished.

Folder management:

- A simple dialog or sheet lists selected folders.
- Actions: add folder, remove folder, close.
- Removing a folder only removes app access and preference state.

## Architecture

Add new music-specific classes rather than expanding backup/restore classes:

- `MusicTrack`: data model.
- `LyricLine` and parsed lyric result model.
- `MusicLibraryService`: MediaStore query, SAF folder scanning, metadata extraction, and library merge.
- `LyricParser`: `.lrc` and embedded lyric parsing helpers.
- `MusicStore`: selected folder URI persistence, system source state, sort mode, folder track cache, and music preferences.
- `OnlineLyricsService`: LRCLIB search and synced/plain lyric retrieval.
- `MusicViewModel`: library state, current track, player state bridge, folder actions, and lyric state.
- `MusicScreen`: Compose UI for playback, lyrics, library, and folder management.

`MainActivity` owns Activity-only integrations:

- Runtime permission launcher for audio read access.
- SAF folder picker launcher.
- Navigation to and from the music screen.

`MusicViewModel` owns ExoPlayer lifecycle through `onCleared()` or an explicit release path. The implementation should avoid leaking player instances across screen exits.

## Error Handling

- Permission denied: show an empty state explaining that system music needs audio permission, while custom folder selection remains available.
- No music found: show an empty state with refresh and add-folder actions.
- Folder revoked or unreadable: keep the app running, mark the source unavailable, and allow removal.
- Playback failure: show a concise message and keep the library usable.
- Lyric parsing failure: fall back to `暂无歌词` without blocking playback.
- Online lyric search failure: keep playback running and fall back to the local empty lyric state.

## Testing And Verification

Implementation should include focused unit tests where practical for:

- `.lrc` timestamp parsing.
- Multiple timestamps per lyric line.
- Current lyric line selection by playback position.
- Track deduplication rules, if extracted into a testable helper.

Manual verification should include:

- `./gradlew.bat assembleDebug`
- Opening the music tool from the toolbox.
- Permission request path for system music.
- Adding and removing a SAF folder.
- Playing at least one supported audio file.
- Seeing top-fixed lyrics for a same-name `.lrc` file.
- Seeing online lyrics when no local lyrics are available.
- Confirming music sort mode persists after leaving and reopening the music screen.
- Confirming Android back returns to the tools tab.

## Follow-Up Plan

Later versions can add:

- Global floating lyrics with overlay permission.
- Manual lyric rematch.
- Playlist and queue editing.
- Background playback notification controls.
