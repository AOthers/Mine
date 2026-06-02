package com.wode.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.exoplayer.ExoPlayer
import com.wode.app.data.Lyrics
import com.wode.app.data.MusicTrack
import com.wode.app.service.LyricParser
import com.wode.app.service.MusicLibraryService
import com.wode.app.service.MusicStore
import com.wode.app.service.OnlineLyricsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FolderSource(
    val uri: Uri,
    val name: String,
)

data class MusicUiState(
    val tracks: List<MusicTrack> = emptyList(),
    val folders: List<FolderSource> = emptyList(),
    val currentTrack: MusicTrack? = null,
    val isLoading: Boolean = false,
    val hasAudioPermission: Boolean = false,
    val isSystemLibraryEnabled: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val currentLyric: String = "\u6682\u65e0\u6b4c\u8bcd",
    val nextLyric: String = "",
    val plainLyrics: String? = null,
    val error: String? = null,
    val playMode: PlayMode = PlayMode.LIST_LOOP,
    val sortMode: MusicSortMode = MusicSortMode.NAME_ASC,
)

enum class MusicSortMode(
    val label: String,
) {
    NAME_ASC("\u540d\u79f0\u6b63\u5e8f"),
    NAME_DESC("\u540d\u79f0\u9006\u5e8f"),
    MODIFIED_ASC("\u4fee\u6539\u65f6\u95f4\u6b63\u5e8f"),
    MODIFIED_DESC("\u4fee\u6539\u65f6\u95f4\u9006\u5e8f"),
}

enum class PlayMode(
    val label: String,
) {
    SINGLE_LOOP("\u5355\u66f2\u5faa\u73af"),
    LIST_LOOP("\u5217\u8868\u5faa\u73af"),
    SHUFFLE("\u968f\u673a\u64ad\u653e"),
}

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val store = MusicStore(application)
    private val libraryService = MusicLibraryService(application)
    private val onlineLyricsService = OnlineLyricsService()
    private val player = ExoPlayer.Builder(application).build()

    private val _uiState = MutableStateFlow(MusicUiState(sortMode = store.getSortMode()))
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var lyrics: Lyrics = Lyrics.Empty
    private var progressJob: Job? = null
    private var hasLoadedLibraryThisRun = false

    init {
        applyPlayMode(PlayMode.LIST_LOOP)
        player.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
                    if (isPlaying) startProgressUpdates() else stopProgressUpdates()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    _uiState.value = _uiState.value.copy(durationMs = player.duration.coerceAtLeast(0L))
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    _uiState.value = _uiState.value.copy(error = error.message ?: "\u64ad\u653e\u5931\u8d25")
                }
            },
        )
    }

    fun loadLibrary(hasAudioPermission: Boolean) {
        viewModelScope.launch {
            val systemEnabled = store.isSystemLibraryEnabled()
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                hasAudioPermission = hasAudioPermission,
                isSystemLibraryEnabled = systemEnabled,
                error = null,
            )
            val folders = store.getFolderUris().map { FolderSource(it, folderNameFromUri(it)) }
            val cachedTracks = withContext(Dispatchers.IO) {
                store.getCachedFolderTracks(folders.map { it.uri })
            }
            if (cachedTracks.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    tracks = sortTracks(cachedTracks, _uiState.value.sortMode),
                    folders = folders,
                    isLoading = true,
                    error = null,
                )
            }
            val tracks = withContext(Dispatchers.IO) {
                libraryService.loadLibrary(
                    folderUris = folders.map { it.uri },
                    includeSystemLibrary = hasAudioPermission && systemEnabled,
                )
            }
            withContext(Dispatchers.IO) {
                store.saveCachedFolderTracks(tracks)
            }
            val sortedTracks = sortTracks(tracks, _uiState.value.sortMode)
            _uiState.value = _uiState.value.copy(
                tracks = sortedTracks,
                folders = folders,
                isLoading = false,
                error = null,
            )
            hasLoadedLibraryThisRun = true
        }
    }

    fun ensureLibraryLoaded(hasAudioPermission: Boolean) {
        if (hasLoadedLibraryThisRun) {
            _uiState.value = _uiState.value.copy(
                hasAudioPermission = hasAudioPermission,
                isSystemLibraryEnabled = store.isSystemLibraryEnabled(),
                folders = store.getFolderUris().map { FolderSource(it, folderNameFromUri(it)) },
            )
            return
        }
        loadLibrary(hasAudioPermission)
    }

    fun setSortMode(sortMode: MusicSortMode) {
        val state = _uiState.value
        val sortedTracks = sortTracks(state.tracks, sortMode)
        store.setSortMode(sortMode)
        _uiState.value = state.copy(tracks = sortedTracks, sortMode = sortMode)
        refreshPlayerQueueForSortedTracks(sortedTracks, state.currentTrack)
    }

    fun isSystemLibraryEnabled(): Boolean {
        return store.isSystemLibraryEnabled()
    }

    fun enableSystemLibrary(hasAudioPermission: Boolean) {
        store.setSystemLibraryEnabled(true)
        loadLibrary(hasAudioPermission)
    }

    fun disableSystemLibrary(hasAudioPermission: Boolean) {
        store.setSystemLibraryEnabled(false)
        loadLibrary(hasAudioPermission)
    }

    fun addFolder(uri: Uri, hasAudioPermission: Boolean) {
        store.addFolderUri(uri)
        loadLibrary(hasAudioPermission)
    }

    fun removeFolder(uri: Uri, hasAudioPermission: Boolean) {
        store.removeFolderUri(uri)
        store.removeCachedTracksForFolder(uri)
        loadLibrary(hasAudioPermission)
    }

    fun playTrack(track: MusicTrack) {
        val tracks = _uiState.value.tracks
        val index = tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        player.setMediaItems(tracks.map { MediaItem.fromUri(it.uri) }, index, 0L)
        player.prepare()
        player.play()
        _uiState.value = _uiState.value.copy(currentTrack = track, positionMs = 0L, durationMs = track.durationMs, error = null)
        loadLyrics(track)
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun playPrevious() {
        if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
            syncCurrentTrackFromPlayer(loadLyricsWhenChanged = true)
        }
    }

    fun playNext() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            syncCurrentTrackFromPlayer(loadLyricsWhenChanged = true)
        }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
        updateProgress()
    }

    fun cyclePlayMode() {
        val next = when (_uiState.value.playMode) {
            PlayMode.SINGLE_LOOP -> PlayMode.LIST_LOOP
            PlayMode.LIST_LOOP -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.SINGLE_LOOP
        }
        applyPlayMode(next)
        _uiState.value = _uiState.value.copy(playMode = next)
        viewModelScope.launch {
            _messages.emit(next.label)
        }
    }

    private fun loadLyrics(track: MusicTrack) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(currentLyric = "\u6b63\u5728\u641c\u7d22\u6b4c\u8bcd...", nextLyric = "", plainLyrics = null)
            val raw = withContext(Dispatchers.IO) {
                libraryService.readLyrics(track) ?: onlineLyricsService.searchLyrics(track)
            }
            lyrics = raw?.let { LyricParser.parseEmbeddedLyrics(it) } ?: Lyrics.Empty
            updateProgress()
        }
    }

    private fun startProgressUpdates() {
        if (progressJob?.isActive == true) return
        progressJob = viewModelScope.launch {
            while (true) {
                updateProgress()
                delay(500)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        updateProgress()
    }

    private fun updateProgress() {
        syncCurrentTrackFromPlayer(loadLyricsWhenChanged = true)
        val position = player.currentPosition.coerceAtLeast(0L)
        val duration = player.duration.takeIf { it > 0 } ?: _uiState.value.currentTrack?.durationMs ?: 0L
        val currentLine = lyrics.currentLine(position)?.text?.takeIf { it.isNotBlank() }
        val upcomingLine = lyrics.nextLine(position)?.text?.takeIf { it.isNotBlank() }
        val displayedLyric = currentLine
            ?: upcomingLine
            ?: lyrics.plainText?.lineSequence()?.firstOrNull { it.isNotBlank() }
            ?: "\u6682\u65e0\u6b4c\u8bcd"
        val nextLine = if (currentLine == null) {
            ""
        } else {
            upcomingLine.orEmpty()
        }
        _uiState.value = _uiState.value.copy(
            positionMs = position,
            durationMs = duration,
            currentLyric = displayedLyric,
            nextLyric = nextLine,
            plainLyrics = lyrics.plainText,
        )
    }

    private fun syncCurrentTrackFromPlayer(loadLyricsWhenChanged: Boolean = false) {
        val index = player.currentMediaItemIndex
        val track = _uiState.value.tracks.getOrNull(index) ?: return
        if (_uiState.value.currentTrack?.id != track.id) {
            _uiState.value = _uiState.value.copy(currentTrack = track, durationMs = track.durationMs)
            if (loadLyricsWhenChanged) loadLyrics(track)
        }
    }

    private fun applyPlayMode(mode: PlayMode) {
        when (mode) {
            PlayMode.SINGLE_LOOP -> {
                player.repeatMode = REPEAT_MODE_ONE
                player.shuffleModeEnabled = false
            }
            PlayMode.LIST_LOOP -> {
                player.repeatMode = REPEAT_MODE_ALL
                player.shuffleModeEnabled = false
            }
            PlayMode.SHUFFLE -> {
                player.repeatMode = REPEAT_MODE_ALL
                player.shuffleModeEnabled = true
            }
        }
    }

    private fun sortTracks(tracks: List<MusicTrack>, sortMode: MusicSortMode): List<MusicTrack> {
        val nameComparator = compareBy<MusicTrack>(
            { it.title.lowercase(java.util.Locale.getDefault()) },
            { it.displayArtist.lowercase(java.util.Locale.getDefault()) },
            { it.uri.toString() },
        )
        val modifiedComparator = compareBy<MusicTrack>(
            { it.modifiedTimeMs },
            { it.title.lowercase(java.util.Locale.getDefault()) },
            { it.uri.toString() },
        )
        return when (sortMode) {
            MusicSortMode.NAME_ASC -> tracks.sortedWith(nameComparator)
            MusicSortMode.NAME_DESC -> tracks.sortedWith(nameComparator.reversed())
            MusicSortMode.MODIFIED_ASC -> tracks.sortedWith(modifiedComparator)
            MusicSortMode.MODIFIED_DESC -> tracks.sortedWith(modifiedComparator.reversed())
        }
    }

    private fun refreshPlayerQueueForSortedTracks(tracks: List<MusicTrack>, currentTrack: MusicTrack?) {
        if (currentTrack == null || player.mediaItemCount == 0) return
        val index = tracks.indexOfFirst { it.id == currentTrack.id }
        if (index < 0) return
        val position = player.currentPosition.coerceAtLeast(0L)
        val shouldPlay = player.isPlaying
        player.setMediaItems(tracks.map { MediaItem.fromUri(it.uri) }, index, position)
        player.prepare()
        if (shouldPlay) {
            player.play()
        }
    }

    private fun folderNameFromUri(uri: Uri): String {
        val decoded = Uri.decode(uri.encodedPath.orEmpty())
        return decoded.substringAfterLast(":").substringAfterLast("/").ifBlank { "\u81ea\u9009\u6587\u4ef6\u5939" }
    }

    override fun onCleared() {
        super.onCleared()
        progressJob?.cancel()
        player.release()
    }
}
