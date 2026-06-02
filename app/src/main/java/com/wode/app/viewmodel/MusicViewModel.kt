package com.wode.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.wode.app.data.Lyrics
import com.wode.app.data.MusicTrack
import com.wode.app.service.LyricParser
import com.wode.app.service.MusicLibraryService
import com.wode.app.service.MusicStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val currentLyric: String = "\u6682\u65e0\u6b4c\u8bcd",
    val nextLyric: String = "",
    val plainLyrics: String? = null,
    val error: String? = null,
)

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val store = MusicStore(application)
    private val libraryService = MusicLibraryService(application)
    private val player = ExoPlayer.Builder(application).build()

    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    private var lyrics: Lyrics = Lyrics.Empty
    private var progressJob: Job? = null

    init {
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
            _uiState.value = _uiState.value.copy(isLoading = true, hasAudioPermission = hasAudioPermission, error = null)
            val folders = store.getFolderUris().map { FolderSource(it, folderNameFromUri(it)) }
            val tracks = withContext(Dispatchers.IO) {
                libraryService.loadLibrary(folders.map { it.uri }, includeSystemLibrary = hasAudioPermission)
            }
            _uiState.value = _uiState.value.copy(
                tracks = tracks,
                folders = folders,
                isLoading = false,
                error = null,
            )
        }
    }

    fun addFolder(uri: Uri, hasAudioPermission: Boolean) {
        store.addFolderUri(uri)
        loadLibrary(hasAudioPermission)
    }

    fun removeFolder(uri: Uri, hasAudioPermission: Boolean) {
        store.removeFolderUri(uri)
        loadLibrary(hasAudioPermission)
    }

    fun playTrack(track: MusicTrack) {
        player.setMediaItem(MediaItem.fromUri(track.uri))
        player.prepare()
        player.play()
        _uiState.value = _uiState.value.copy(currentTrack = track, positionMs = 0L, durationMs = track.durationMs, error = null)
        loadLyrics(track)
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun playPrevious() {
        val current = _uiState.value.currentTrack ?: return
        val tracks = _uiState.value.tracks
        val index = tracks.indexOfFirst { it.id == current.id }
        if (index > 0) playTrack(tracks[index - 1])
    }

    fun playNext() {
        val current = _uiState.value.currentTrack ?: return
        val tracks = _uiState.value.tracks
        val index = tracks.indexOfFirst { it.id == current.id }
        if (index >= 0 && index < tracks.lastIndex) playTrack(tracks[index + 1])
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
        updateProgress()
    }

    private fun loadLyrics(track: MusicTrack) {
        viewModelScope.launch {
            val raw = withContext(Dispatchers.IO) { libraryService.readLyrics(track) }
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
        val position = player.currentPosition.coerceAtLeast(0L)
        val duration = player.duration.takeIf { it > 0 } ?: _uiState.value.currentTrack?.durationMs ?: 0L
        val currentLine = lyrics.currentLine(position)?.text
        val nextLine = lyrics.nextLine(position)?.text.orEmpty()
        _uiState.value = _uiState.value.copy(
            positionMs = position,
            durationMs = duration,
            currentLyric = currentLine ?: lyrics.plainText?.lineSequence()?.firstOrNull().orEmpty().ifBlank { "\u6682\u65e0\u6b4c\u8bcd" },
            nextLyric = nextLine,
            plainLyrics = lyrics.plainText,
        )
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
