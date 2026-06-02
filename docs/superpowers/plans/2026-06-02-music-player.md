# Music Player Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an in-app local music player with system music discovery, custom folder sources, common-format playback, and fixed top lyrics.

**Architecture:** Add music-specific data, store, library, lyric, ViewModel, and Compose screen classes. Keep Activity-only permission and folder-picker integrations in `MainActivity`, and wire the music tool into the existing toolbox and favorites patterns.

**Tech Stack:** Android Kotlin, Jetpack Compose Material3, DataStore Preferences, Android MediaStore/SAF, AndroidX Media3 ExoPlayer, Kotlin/JUnit unit tests.

---

### Task 1: Lyric Parsing Core

**Files:**
- Create: `app/src/main/java/com/wode/app/data/Lyrics.kt`
- Create: `app/src/main/java/com/wode/app/service/LyricParser.kt`
- Create: `app/src/test/java/com/wode/app/service/LyricParserTest.kt`
- Modify: `app/build.gradle.kts`

- [x] Add JUnit test dependency.
- [x] Write failing tests for `.lrc` timestamp parsing, multiple timestamps, and current-line selection.
- [x] Implement `LyricLine`, `Lyrics`, and `LyricParser`.
- [x] Run `./gradlew.bat testDebugUnitTest` and record that this local path environment reports `ClassNotFoundException` for the discovered test class.

### Task 2: Music Library And Preferences

**Files:**
- Create: `app/src/main/java/com/wode/app/data/MusicTrack.kt`
- Create: `app/src/main/java/com/wode/app/service/MusicStore.kt`
- Create: `app/src/main/java/com/wode/app/service/MusicLibraryService.kt`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`

- [x] Add Media3 dependencies and audio read permissions.
- [x] Implement selected-folder URI persistence.
- [x] Implement MediaStore scanning for playable music rows.
- [x] Implement SAF folder scanning for supported audio extensions and sibling `.lrc`.
- [x] Deduplicate tracks by URI.

### Task 3: Music Playback ViewModel

**Files:**
- Create: `app/src/main/java/com/wode/app/viewmodel/MusicViewModel.kt`

- [x] Create music UI state types.
- [x] Own an ExoPlayer instance and release it in `onCleared()`.
- [x] Load library from system and custom folder sources.
- [x] Play selected tracks, previous/next, play/pause, seek, and expose progress.
- [x] Load lyrics for the current track and compute current/next lyric lines from playback position.

### Task 4: Music Compose Screen

**Files:**
- Create: `app/src/main/java/com/wode/app/ui/screens/MusicScreen.kt`

- [x] Build top app bar with back, refresh, and folder actions.
- [x] Build fixed lyric strip below the app bar.
- [x] Build playback controls and progress slider.
- [x] Build music list, empty states, errors, and folder management dialog.

### Task 5: Navigation, Permissions, And Toolbox Wiring

**Files:**
- Modify: `app/src/main/java/com/wode/app/MainActivity.kt`
- Modify: `app/src/main/java/com/wode/app/ui/screens/ToolboxScreens.kt`
- Modify: `app/src/main/java/com/wode/app/service/FavoriteStore.kt`

- [x] Add `Screen.Music`.
- [x] Add audio permission launcher and music folder picker launcher.
- [x] Wire `MusicViewModel` events to Activity launchers.
- [x] Add music tool card to tools and favorites tabs.
- [x] Add favorite persistence for music.
- [x] Ensure Android back returns from music to tools.

### Task 6: Verification

**Files:**
- Modify docs only if implementation meaningfully differs from the design.

- [x] Run `./gradlew.bat testDebugUnitTest`; blocked by `ClassNotFoundException: com.wode.app.service.LyricParserTest` even though the test class is compiled under the Chinese workspace path.
- [x] Run `./gradlew.bat assembleDebug`.
- [x] Inspect `git diff` for unrelated changes.
- [x] Commit implementation.

### Post-Plan Iteration Summary

After the initial plan, the music player gained:

- Source management sheets for one-tap system music and selected folders.
- Persisted sort mode with name/modified-time ascending and descending options.
- Folder scan caching so reopening the music page does not force a slow rescan.
- Playback modes for repeat one, repeat list, and shuffle.
- Online LRCLIB lyric lookup without default caching.
- A fixed-height lyric strip with marquee scrolling for long current lines.
