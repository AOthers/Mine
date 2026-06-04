package com.wode.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wode.app.data.MusicSource
import com.wode.app.data.MusicTrack
import com.wode.app.viewmodel.FolderSource
import com.wode.app.viewmodel.MusicSortMode
import com.wode.app.viewmodel.MusicViewModel
import com.wode.app.viewmodel.PlayMode

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
    val context = LocalContext.current
    var showFolders by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }
    var actionTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var renamingTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var deletingTrack by remember { mutableStateOf<MusicTrack?>(null) }
    val listState = rememberLazyListState()
    var hasPositionedOnOpen by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(state.tracks.map { it.id }) {
        if (hasPositionedOnOpen || state.tracks.isEmpty()) return@LaunchedEffect
        hasPositionedOnOpen = true
        val currentTrackId = state.currentTrack?.id ?: return@LaunchedEffect
        val currentIndex = state.tracks.indexOfFirst { it.id == currentTrackId }
        if (currentIndex >= 0) {
            listState.animateScrollToItem((currentIndex - 2).coerceAtLeast(0))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "\u97f3\u4e50\u64ad\u653e\u5668",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "\u8fd4\u56de")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "\u5237\u65b0")
                    }
                    IconButton(onClick = { showSortDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "\u5207\u6362\u6392\u5e8f")
                    }
                    IconButton(onClick = { showFolders = true }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "\u6587\u4ef6\u5939")
                    }
                    IconButton(onClick = { showSources = true }) {
                        Icon(Icons.Default.LibraryMusic, contentDescription = "\u97f3\u4e50\u6765\u6e90")
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
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }
            if (state.isLoading) {
                Text(
                    "\u6b63\u5728\u626b\u63cf\u97f3\u4e50...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.tracks.isEmpty() && !state.isLoading) {
                EmptyMusicState(
                    modifier = Modifier.weight(1f),
                    onAddFolder = onAddFolder,
                    onRequestPermission = onRequestPermission,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.tracks, key = { it.id }) { track ->
                        TrackRow(
                            track = track,
                            isPlaying = state.currentTrack?.id == track.id,
                            onClick = { viewModel.playTrack(track) },
                            onLongClick = { actionTrack = track },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            PlayerControls(
                title = state.currentTrack?.title ?: "\u672a\u64ad\u653e",
                artist = state.currentTrack?.displayArtist ?: "\u9009\u62e9\u4e00\u9996\u6b4c\u66f2\u5f00\u59cb",
                isPlaying = state.isPlaying,
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                playMode = state.playMode,
                onPrevious = viewModel::playPrevious,
                onPlayPause = viewModel::togglePlayPause,
                onNext = viewModel::playNext,
                onSeek = viewModel::seekTo,
                onCyclePlayMode = viewModel::cyclePlayMode,
            )
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

    if (showSortDialog) {
        SortDialog(
            selected = state.sortMode,
            onSelect = {
                viewModel.setSortMode(it)
                showSortDialog = false
            },
            onClose = { showSortDialog = false },
        )
    }

    if (showSources) {
        SourceDialog(
            isSystemLibraryEnabled = state.isSystemLibraryEnabled,
            onToggleSystemLibrary = {
                if (state.isSystemLibraryEnabled) {
                    viewModel.disableSystemLibrary(state.hasAudioPermission)
                } else {
                    onRequestPermission()
                }
                showSources = false
            },
            onAddFolder = {
                showSources = false
                onAddFolder()
            },
            onClose = { showSources = false },
        )
    }

    actionTrack?.let { track ->
        TrackActionDialog(
            track = track,
            onDismiss = { actionTrack = null },
            onRename = {
                renamingTrack = track
                actionTrack = null
            },
            onDelete = {
                deletingTrack = track
                actionTrack = null
            },
        )
    }

    renamingTrack?.let { track ->
        RenameTrackDialog(
            track = track,
            onDismiss = { renamingTrack = null },
            onSave = { title, artist ->
                viewModel.renameTrack(track, title, artist)
                renamingTrack = null
            },
        )
    }

    deletingTrack?.let { track ->
        DeleteTrackDialog(
            track = track,
            onDismiss = { deletingTrack = null },
            onRemoveFromApp = {
                viewModel.removeTrackFromApp(track)
                deletingTrack = null
            },
            onDeleteFile = {
                viewModel.deleteTrackFile(track)
                deletingTrack = null
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LyricStrip(current: String, next: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            current,
            modifier = Modifier
                .fillMaxWidth()
                .basicMarquee(iterations = Int.MAX_VALUE),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        if (next.isNotBlank()) {
            Text(
                next,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(iterations = Int.MAX_VALUE),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
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
    playMode: PlayMode,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onCyclePlayMode: () -> Unit,
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
                IconButton(onClick = onCyclePlayMode) {
                    Icon(playMode.icon, contentDescription = playMode.label)
                }
            }
        }
    }
}

private val PlayMode.icon: ImageVector
    get() = when (this) {
        PlayMode.SINGLE_LOOP -> Icons.Default.RepeatOne
        PlayMode.LIST_LOOP -> Icons.Default.Repeat
        PlayMode.SHUFFLE -> Icons.Default.Shuffle
    }

@Composable
private fun EmptyMusicState(
    modifier: Modifier = Modifier,
    onAddFolder: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("\u8fd8\u6ca1\u6709\u8bc6\u522b\u5230\u97f3\u4e50", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onRequestPermission) { Text("\u4e00\u952e\u8bc6\u522b") }
                OutlinedButton(onClick = onAddFolder) { Text("\u9009\u62e9\u6587\u4ef6\u5939") }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(
    track: MusicTrack,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
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
private fun TrackActionDialog(
    track: MusicTrack,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("\u6b4c\u66f2\u64cd\u4f5c") },
        text = {
            Text(
                track.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        confirmButton = {
            TextButton(onClick = onRename) {
                Text("\u91cd\u547d\u540d")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text("\u5220\u9664")
                }
                TextButton(onClick = onDismiss) {
                    Text("\u53d6\u6d88")
                }
            }
        },
    )
}

@Composable
private fun RenameTrackDialog(
    track: MusicTrack,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var title by remember(track.id) { mutableStateOf(track.title) }
    var artist by remember(track.id) { mutableStateOf(track.artist) }
    val canSave = title.trim().isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("\u91cd\u547d\u540d\u6b4c\u66f2") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("\u6b4c\u66f2\u540d\u79f0") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("\u6b4c\u624b\u540d\u79f0") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, artist) },
                enabled = canSave,
            ) {
                Text("\u4fdd\u5b58")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("\u53d6\u6d88")
            }
        },
    )
}

@Composable
private fun DeleteTrackDialog(
    track: MusicTrack,
    onDismiss: () -> Unit,
    onRemoveFromApp: () -> Unit,
    onDeleteFile: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("\u5220\u9664\u6b4c\u66f2") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    track.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "\u4ec5\u4ece\u672c\u8f6f\u4ef6\u79fb\u9664\u4e0d\u4f1a\u5220\u9664\u624b\u673a\u91cc\u7684\u97f3\u4e50\u6587\u4ef6\uff1b\u5220\u9664\u539f\u6587\u4ef6\u4f1a\u5c1d\u8bd5\u76f4\u63a5\u5220\u9664\u624b\u673a\u91cc\u7684\u8fd9\u9996\u6b4c\u3002",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onRemoveFromApp) {
                Text("\u4ec5\u4ece\u672c\u8f6f\u4ef6\u79fb\u9664")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDeleteFile) {
                    Text("\u5220\u9664\u539f\u6587\u4ef6", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) {
                    Text("\u53d6\u6d88")
                }
            }
        },
    )
}

@Composable
private fun FolderDialog(
    folders: List<FolderSource>,
    hasAudioPermission: Boolean,
    onAddFolder: () -> Unit,
    onRemoveFolder: (Uri) -> Unit,
    onClose: () -> Unit,
) {
    AppBottomSheet(onDismiss = onClose) {
        SheetHeader(
            title = "\u97f3\u4e50\u6587\u4ef6\u5939",
            subtitle = if (folders.isEmpty()) "\u8fd8\u6ca1\u6709\u6dfb\u52a0\u81ea\u9009\u6587\u4ef6\u5939" else "\u7ba1\u7406\u5df2\u9009\u7684\u97f3\u4e50\u76ee\u5f55",
        )
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            folders.forEach { folder ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            folder.name,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TextButton(onClick = { onRemoveFolder(folder.uri) }) {
                            Text("\u79fb\u9664")
                        }
                    }
                }
            }
            if (!hasAudioPermission) {
                Text("\u7cfb\u7edf\u97f3\u4e50\u6388\u6743\u672a\u5f00\u542f\uff0c\u81ea\u9009\u6587\u4ef6\u5939\u4ecd\u53ef\u4f7f\u7528\u3002", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SheetActions(
            primaryText = "\u6dfb\u52a0\u6587\u4ef6\u5939",
            onPrimary = onAddFolder,
            secondaryText = "\u5173\u95ed",
            onSecondary = onClose,
        )
    }
}

@Composable
private fun SourceDialog(
    isSystemLibraryEnabled: Boolean,
    onToggleSystemLibrary: () -> Unit,
    onAddFolder: () -> Unit,
    onClose: () -> Unit,
) {
    AppBottomSheet(onDismiss = onClose) {
        SheetHeader(
            title = "\u97f3\u4e50\u6765\u6e90",
            subtitle = "\u9009\u62e9\u7cfb\u7edf\u97f3\u4e50\u6216\u81ea\u9009\u6587\u4ef6\u5939",
        )
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SheetOptionRow(
                icon = Icons.Default.LibraryMusic,
                title = if (isSystemLibraryEnabled) "\u5173\u95ed\u7cfb\u7edf\u97f3\u4e50\u8bc6\u522b" else "\u4e00\u952e\u8bc6\u522b\u7cfb\u7edf\u97f3\u4e50",
                subtitle = if (isSystemLibraryEnabled) "\u5f53\u524d\u5df2\u5f00\u542f" else "\u4ece\u624b\u673a\u5a92\u4f53\u5e93\u5feb\u901f\u8bfb\u53d6",
                selected = isSystemLibraryEnabled,
                onClick = onToggleSystemLibrary,
            )
            SheetOptionRow(
                icon = Icons.Default.FolderOpen,
                title = "\u9009\u62e9\u6587\u4ef6\u5939",
                subtitle = "\u6dfb\u52a0\u6216\u626b\u63cf\u6307\u5b9a\u76ee\u5f55",
                onClick = onAddFolder,
            )
        }
        SheetActions(
            secondaryText = "\u53d6\u6d88",
            onSecondary = onClose,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortDialog(
    selected: MusicSortMode,
    onSelect: (MusicSortMode) -> Unit,
    onClose: () -> Unit,
) {
    AppBottomSheet(onDismiss = onClose) {
        SheetHeader(
            title = "\u6392\u5e8f\u65b9\u5f0f",
            subtitle = selected.label,
        )
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            musicSortModes.forEach { mode ->
                SheetOptionRow(
                    icon = mode.icon,
                    title = mode.label,
                    selected = mode == selected,
                    onClick = { onSelect(mode) },
                )
            }
        }
        SheetActions(
            secondaryText = "\u53d6\u6d88",
            onSecondary = onClose,
        )
    }
}

@Composable
private fun SheetOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "\u5df2\u9009",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SheetHeader(title: String, subtitle: String? = null) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
    if (subtitle != null) {
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SheetActions(
    primaryText: String? = null,
    onPrimary: (() -> Unit)? = null,
    secondaryText: String,
    onSecondary: () -> Unit,
) {
    Spacer(Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onSecondary,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(secondaryText)
        }
        if (primaryText != null && onPrimary != null) {
            TextButton(
                onClick = onPrimary,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(primaryText)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            content = content,
        )
    }
}

private val musicSortModes = arrayOf(
    MusicSortMode.NAME_ASC,
    MusicSortMode.NAME_DESC,
    MusicSortMode.MODIFIED_ASC,
    MusicSortMode.MODIFIED_DESC,
)

private val MusicSortMode.icon: ImageVector
    get() = when (this) {
        MusicSortMode.NAME_ASC,
        MusicSortMode.NAME_DESC -> Icons.AutoMirrored.Filled.Sort
        MusicSortMode.MODIFIED_ASC,
        MusicSortMode.MODIFIED_DESC -> Icons.Default.Refresh
    }

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
