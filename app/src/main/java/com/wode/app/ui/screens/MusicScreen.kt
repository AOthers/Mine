package com.wode.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wode.app.data.MusicSource
import com.wode.app.data.MusicTrack
import com.wode.app.viewmodel.FolderSource
import com.wode.app.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onAddFolder: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var showFolders by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("\u97f3\u4e50") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "\u8fd4\u56de")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "\u5237\u65b0")
                    }
                    IconButton(onClick = { showFolders = true }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "\u6587\u4ef6\u5939")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
        ) {
            LyricStrip(current = state.currentLyric, next = state.nextLyric)
            Spacer(Modifier.height(12.dp))
            PlayerControls(
                title = state.currentTrack?.title ?: "\u672a\u64ad\u653e",
                artist = state.currentTrack?.displayArtist ?: "\u9009\u62e9\u4e00\u9996\u6b4c\u66f2\u5f00\u59cb",
                isPlaying = state.isPlaying,
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                onPrevious = viewModel::playPrevious,
                onPlayPause = viewModel::togglePlayPause,
                onNext = viewModel::playNext,
                onSeek = viewModel::seekTo,
            )
            Spacer(Modifier.height(12.dp))
            if (!state.hasAudioPermission) {
                PermissionCard(onRequestPermission = onRequestPermission)
                Spacer(Modifier.height(12.dp))
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (state.tracks.isEmpty() && !state.isLoading) {
                EmptyMusicState(onAddFolder = onAddFolder, onRequestPermission = onRequestPermission)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.tracks, key = { it.id }) { track ->
                        TrackRow(
                            track = track,
                            isPlaying = state.currentTrack?.id == track.id,
                            onClick = { viewModel.playTrack(track) },
                        )
                    }
                }
            }
        }
    }

    if (showFolders) {
        FolderDialog(
            folders = state.folders,
            hasAudioPermission = state.hasAudioPermission,
            onAddFolder = onAddFolder,
            onRemoveFolder = { viewModel.removeFolder(it, state.hasAudioPermission) },
            onClose = { showFolders = false },
        )
    }
}

@Composable
private fun LyricStrip(current: String, next: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                current,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (next.isNotBlank()) {
                Text(
                    next,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PlayerControls(
    title: String,
    artist: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = positionMs.toFloat().coerceIn(0f, durationMs.coerceAtLeast(1L).toFloat()),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(positionMs), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                Text(formatDuration(durationMs), style = MaterialTheme.typography.labelSmall)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "\u4e0a\u4e00\u9996")
                }
                IconButton(onClick = onPlayPause) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "\u64ad\u653e\u6682\u505c")
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = "\u4e0b\u4e00\u9996")
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(onRequestPermission: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("\u6388\u6743\u540e\u53ef\u8bc6\u522b\u7cfb\u7edf\u97f3\u4e50", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRequestPermission) {
                Text("\u6388\u6743")
            }
        }
    }
}

@Composable
private fun EmptyMusicState(onAddFolder: () -> Unit, onRequestPermission: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("\u8fd8\u6ca1\u6709\u8bc6\u522b\u5230\u97f3\u4e50", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onRequestPermission) { Text("\u6388\u6743\u7cfb\u7edf\u97f3\u4e50") }
                OutlinedButton(onClick = onAddFolder) { Text("\u6dfb\u52a0\u6587\u4ef6\u5939") }
            }
        }
    }
}

@Composable
private fun TrackRow(track: MusicTrack, isPlaying: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.displayArtist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                if (track.source == MusicSource.SYSTEM) "\u7cfb\u7edf" else track.folderName ?: "\u6587\u4ef6\u5939",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun FolderDialog(
    folders: List<FolderSource>,
    hasAudioPermission: Boolean,
    onAddFolder: () -> Unit,
    onRemoveFolder: (Uri) -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("\u97f3\u4e50\u6587\u4ef6\u5939") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (folders.isEmpty()) {
                    Text("\u8fd8\u6ca1\u6709\u6dfb\u52a0\u81ea\u9009\u6587\u4ef6\u5939", style = MaterialTheme.typography.bodyMedium)
                }
                folders.forEach { folder ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(folder.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { onRemoveFolder(folder.uri) }) {
                            Text("\u79fb\u9664")
                        }
                    }
                }
                if (!hasAudioPermission) {
                    Text("\u7cfb\u7edf\u97f3\u4e50\u6388\u6743\u672a\u5f00\u542f\uff0c\u81ea\u9009\u6587\u4ef6\u5939\u4ecd\u53ef\u4f7f\u7528\u3002", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAddFolder) {
                Text("\u6dfb\u52a0\u6587\u4ef6\u5939")
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text("\u5173\u95ed")
            }
        },
    )
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
