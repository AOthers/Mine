package com.wode.app.ui.screens

import android.content.res.Configuration
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.wode.app.data.ReaderChapter
import com.wode.app.data.ReaderContentBlock
import com.wode.app.data.ReaderItem
import com.wode.app.data.ReaderItemType
import com.wode.app.data.ReaderPageMode
import com.wode.app.data.ReaderSettings
import com.wode.app.data.ReaderTheme
import com.wode.app.viewmodel.ReaderUiState
import com.wode.app.viewmodel.ReaderViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    onAddFile: () -> Unit,
    onAddFolder: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val selected = state.selectedItem
    BackHandler {
        if (selected == null) onBack() else viewModel.closeReader()
    }
    when {
        selected == null -> ReaderBookshelfScreen(
            state = state,
            onBack = onBack,
            onAddFile = onAddFile,
            onAddFolder = onAddFolder,
            onOpen = viewModel::openItem,
            onRemove = viewModel::removeItem,
            onRename = viewModel::renameItem,
        )

        selected.type == ReaderItemType.PDF -> PdfReaderScreen(
            state = state,
            onBack = viewModel::closeReader,
            onProgress = viewModel::updatePdfProgress,
            onEnsurePages = viewModel::ensurePdfPagesAround,
            onChapter = viewModel::jumpToChapter,
            onJumpConsumed = viewModel::consumeJumpRequest,
        )

        selected.type == ReaderItemType.COMIC_FOLDER || selected.type == ReaderItemType.COMIC_ARCHIVE -> ComicReaderScreen(
            state = state,
            onBack = viewModel::closeReader,
            onProgress = viewModel::updateComicProgress,
            onChapter = viewModel::jumpToChapter,
            onJumpConsumed = viewModel::consumeJumpRequest,
            onSettings = viewModel::updateSettings,
        )

        else -> TextReaderScreen(
            state = state,
            onBack = viewModel::closeReader,
            onProgress = viewModel::updateTextProgress,
            onChapter = viewModel::jumpToChapter,
            onJumpConsumed = viewModel::consumeJumpRequest,
            onSettings = viewModel::updateSettings,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderBookshelfScreen(
    state: ReaderUiState,
    onBack: () -> Unit,
    onAddFile: () -> Unit,
    onAddFolder: () -> Unit,
    onOpen: (ReaderItem) -> Unit,
    onRemove: (ReaderItem, Boolean) -> Unit,
    onRename: (ReaderItem, String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("阅读器") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onAddFile) {
                        Icon(Icons.Default.Add, contentDescription = "添加文件")
                    }
                    IconButton(onClick = onAddFolder) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "添加文件夹")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (state.items.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("还没有书", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onAddFile) { Text("添加文件") }
                        Button(onClick = onAddFolder) { Text("添加文件夹") }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.items, key = { it.id }) { item ->
                    ReaderItemCard(
                        item = item,
                        onOpen = { onOpen(item) },
                        onRemove = { deleteSource -> onRemove(item, deleteSource) },
                        onRename = { title -> onRename(item, title) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReaderItemCard(
    item: ReaderItem,
    onOpen: () -> Unit,
    onRemove: (Boolean) -> Unit,
    onRename: (String) -> Unit,
) {
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var title by remember(item.title) { mutableStateOf(item.title) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = { showRename = true }),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(readerIcon(item.type), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(item.displayPath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(item.progressLabel.ifBlank { "未开始" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { showDelete = true }) {
                Icon(Icons.Default.Delete, contentDescription = "移除")
            }
        }
    }
    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("重命名") },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    label = { Text("名称") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(title)
                        showRename = false
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("取消") }
            },
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除阅读项目") },
            text = { Text("要如何删除“${item.title}”？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDelete = false
                        onRemove(true)
                    },
                ) {
                    Text("删除源文件", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showDelete = false
                            onRemove(false)
                        },
                    ) {
                        Text("仅在软件中删除")
                    }
                    TextButton(onClick = { showDelete = false }) {
                        Text("取消")
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun TextReaderScreen(
    state: ReaderUiState,
    onBack: () -> Unit,
    onProgress: (Int, Int) -> Unit,
    onChapter: (ReaderChapter) -> Unit,
    onJumpConsumed: () -> Unit,
    onSettings: (ReaderSettings) -> Unit,
) {
    val content = state.textContent
    val blocks = remember(content?.blocks) { content?.blocks ?: emptyList() }
    var showChapters by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var showChrome by remember(isLandscape) { mutableStateOf(!isLandscape) }

    val colors = readerThemeColors(state.settings.theme)
    Scaffold(
        topBar = if (showChrome) {
            {
            ReaderTopBar(
                title = content?.title ?: "阅读",
                background = colors.first,
                onBack = onBack,
                onShowChapters = { showChapters = true },
                onShowSettings = { showSettings = true },
            )
            }
        } else {
            {}
        },
    ) { padding ->
        val error = state.errorMessage
        when {
            error != null -> ReaderError(modifier = Modifier.padding(padding), message = error, onBack = onBack)
            state.isLoading || content == null -> LoadingReader(modifier = Modifier.padding(padding), text = "正在加载...")
            blocks.isNotEmpty() -> {
                val initialIndex = state.currentIndex.coerceIn(0, blocks.size - 1)
                    key(state.selectedItem?.id, blocks.size, state.settings.pageMode) {
                    TextReaderContent(
                        blocks = blocks,
                        initialIndex = initialIndex,
                        settings = state.settings,
                        background = colors.first,
                        textColor = colors.second,
                        modifier = Modifier
                            .padding(padding)
                            .clickable(enabled = isLandscape) { showChrome = !showChrome },
                        jumpToIndex = state.jumpToIndex,
                        onProgress = onProgress,
                        onJumpConsumed = onJumpConsumed,
                    )
                }
            }
        }
    }
    if (showChapters) {
        ReaderChapterSheet(
            chapters = state.chapters,
            currentIndex = state.currentIndex,
            onDismiss = { showChapters = false },
            onSelect = {
                showChapters = false
                onChapter(it)
            },
        )
    }
    if (showSettings) {
        ReaderSettingsSheet(
            settings = state.settings,
            onDismiss = { showSettings = false },
            onSettings = onSettings,
            showTextSettings = true,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TextReaderContent(
    blocks: List<ReaderContentBlock>,
    initialIndex: Int,
    settings: ReaderSettings,
    background: Color,
    textColor: Color,
    modifier: Modifier,
    jumpToIndex: Int?,
    onProgress: (Int, Int) -> Unit,
    onJumpConsumed: () -> Unit,
) {
    if (settings.pageMode == ReaderPageMode.PAGE) {
        TextPageReaderContent(
            blocks = blocks,
            initialIndex = initialIndex,
            settings = settings,
            background = background,
            textColor = textColor,
            modifier = modifier,
            jumpToIndex = jumpToIndex,
            onProgress = onProgress,
            onJumpConsumed = onJumpConsumed,
        )
        return
    }

    val safeInitial = initialIndex.coerceIn(0, blocks.size - 1)
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = safeInitial,
    )
    var readyToSave by remember { mutableStateOf(false) }
    var lastReportedIndex by remember { mutableStateOf(safeInitial) }
    val currentIndex by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }

    LaunchedEffect(Unit) {
        lastReportedIndex = currentIndex.coerceIn(0, blocks.size - 1)
        readyToSave = true
    }
    LaunchedEffect(jumpToIndex, blocks.size) {
        val target = jumpToIndex ?: return@LaunchedEffect
        val safeTarget = target.coerceIn(0, blocks.size - 1)
        listState.scrollToItem(safeTarget)
        lastReportedIndex = safeTarget
        onProgress(safeTarget, blocks.size)
        onJumpConsumed()
    }
    LaunchedEffect(currentIndex, jumpToIndex, readyToSave) {
        if (jumpToIndex == null && readyToSave && currentIndex != lastReportedIndex) {
            lastReportedIndex = currentIndex
            onProgress(currentIndex, blocks.size)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(blocks) { block ->
            ReaderBlock(block = block, settings = settings, textColor = textColor)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TextPageReaderContent(
    blocks: List<ReaderContentBlock>,
    initialIndex: Int,
    settings: ReaderSettings,
    background: Color,
    textColor: Color,
    modifier: Modifier,
    jumpToIndex: Int?,
    onProgress: (Int, Int) -> Unit,
    onJumpConsumed: () -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        val density = LocalDensity.current
        val pageWidthPx = with(density) { maxWidth.roundToPx() }
        val pageHeightPx = with(density) { maxHeight.roundToPx() }
        val pages = remember(blocks, settings.fontSizeSp, settings.firstLineIndent, pageWidthPx, pageHeightPx, density.density) {
            paginateBlocksForPageMode(
                blocks = blocks,
                settings = settings,
                pageWidthPx = pageWidthPx,
                pageHeightPx = pageHeightPx,
                density = density.density,
            )
        }
        val pageCount = pages.size.coerceAtLeast(1)
        val safeInitial = pages.indexOfFirst { it.sourceIndex >= initialIndex }
            .takeIf { it >= 0 }
            ?: initialIndex.coerceIn(0, pageCount - 1)
        val pagerState = rememberPagerState(initialPage = safeInitial) { pageCount }
        val scope = rememberCoroutineScope()
        var readyToSave by remember { mutableStateOf(false) }
        var lastReportedIndex by remember { mutableStateOf(safeInitial) }
        val currentIndex by remember { derivedStateOf { pagerState.currentPage } }

        LaunchedEffect(Unit) {
            lastReportedIndex = currentIndex.coerceIn(0, pageCount - 1)
            readyToSave = true
        }
        LaunchedEffect(jumpToIndex, pageCount) {
            val target = jumpToIndex ?: return@LaunchedEffect
            val safeTarget = pages.indexOfFirst { it.sourceIndex >= target }
                .takeIf { it >= 0 }
                ?: target.coerceIn(0, pageCount - 1)
            pagerState.scrollToPage(safeTarget)
            lastReportedIndex = safeTarget
            onProgress(pages[safeTarget].sourceIndex, blocks.size)
            onJumpConsumed()
        }
        LaunchedEffect(currentIndex, jumpToIndex, readyToSave) {
            if (jumpToIndex == null && readyToSave && currentIndex != lastReportedIndex) {
                lastReportedIndex = currentIndex
                onProgress(pages[currentIndex].sourceIndex, blocks.size)
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { index ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pageTapNavigation(
                        onPrevious = { scope.launch { pagerState.scrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) } },
                        onNext = { scope.launch { pagerState.scrollToPage((pagerState.currentPage + 1).coerceAtMost(pageCount - 1)) } },
                    ),
            ) {
                ReaderBlock(block = pages[index].block, settings = settings, textColor = textColor, applyFirstLineIndent = false)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ComicReaderScreen(
    state: ReaderUiState,
    onBack: () -> Unit,
    onProgress: (Int) -> Unit,
    onChapter: (ReaderChapter) -> Unit,
    onJumpConsumed: () -> Unit,
    onSettings: (ReaderSettings) -> Unit,
) {
    val item = state.selectedItem
    val images = state.comicImages
    var showChapters by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var previewImageUri by remember { mutableStateOf<String?>(null) }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var showChrome by remember(isLandscape) { mutableStateOf(!isLandscape) }
    val initialIndex = if (images.isEmpty()) 0 else state.currentIndex.coerceIn(0, images.size - 1)
    val pagerState = rememberPagerState { images.size.coerceAtLeast(1) }
    val scope = rememberCoroutineScope()
    var readyToSave by remember(item?.id, images.size, state.settings.pageMode) { mutableStateOf(false) }
    var lastReportedIndex by remember(item?.id, images.size, state.settings.pageMode) { mutableStateOf(initialIndex) }
    val currentIndex by remember {
        derivedStateOf { pagerState.currentPage }
    }

    LaunchedEffect(item?.id, images.size, state.settings.pageMode) {
        if (images.isNotEmpty()) {
            readyToSave = false
            pagerState.scrollToPage(initialIndex)
            lastReportedIndex = currentIndex.coerceIn(0, images.size - 1)
            readyToSave = true
        }
    }
    LaunchedEffect(state.jumpToIndex, state.settings.pageMode, images.size) {
        val target = state.jumpToIndex ?: return@LaunchedEffect
        val safeTarget = target.coerceIn(0, (images.size - 1).coerceAtLeast(0))
        pagerState.scrollToPage(safeTarget)
        lastReportedIndex = safeTarget
        onProgress(safeTarget)
        onJumpConsumed()
    }
    LaunchedEffect(currentIndex, state.jumpToIndex, readyToSave) {
        if (images.isNotEmpty() && state.jumpToIndex == null && readyToSave && currentIndex != lastReportedIndex) {
            lastReportedIndex = currentIndex
            onProgress(currentIndex)
        }
    }

    Scaffold(
        topBar = if (showChrome) {
            {
            ReaderTopBar(
                title = item?.title ?: "漫画",
                background = Color.Black,
                titleColor = Color.White,
                iconColor = Color.White,
                onBack = onBack,
                onShowChapters = { showChapters = true },
                onShowSettings = { showSettings = true },
            )
            }
        } else {
            {}
        },
    ) { padding ->
        val error = state.errorMessage
        when {
            error != null -> ReaderError(modifier = Modifier.padding(padding), message = error, onBack = onBack, dark = true)
            state.isLoading || state.comicImages.isEmpty() -> LoadingReader(modifier = Modifier.padding(padding), text = "正在加载漫画...", dark = true)
            true -> {
                Box(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable(enabled = isLandscape) { showChrome = !showChrome },
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                    ) { index ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pageTapNavigation(
                                    onPrevious = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) } },
                                    onNext = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(state.comicImages.size - 1)) } },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            val image = state.comicImages[index]
                            var imageRatio by remember(image.uri) { mutableStateOf<Float?>(null) }
                            BoxWithConstraints(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                val ratio = imageRatio
                                val fittedModifier = if (ratio == null) {
                                    Modifier.fillMaxSize()
                                } else if (maxWidth.value / maxHeight.value > ratio) {
                                    Modifier
                                        .fillMaxHeight()
                                        .width(maxHeight * ratio)
                                } else {
                                    Modifier
                                        .fillMaxWidth()
                                        .height(maxWidth / ratio)
                                }
                                AsyncImage(
                                    model = image.uri,
                                    contentDescription = image.name,
                                    onSuccess = { success ->
                                        val drawable = success.result.drawable
                                        val width = drawable.intrinsicWidth
                                        val height = drawable.intrinsicHeight
                                        if (width > 0 && height > 0) {
                                            imageRatio = width.toFloat() / height.toFloat()
                                        }
                                    },
                                    modifier = fittedModifier
                                        .then(
                                            if (ratio == null) {
                                                Modifier
                                            } else {
                                                Modifier.clickable { previewImageUri = image.uri }
                                            },
                                        ),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (showChapters) {
        ReaderChapterSheet(
            chapters = state.chapters,
            currentIndex = state.currentIndex,
            onDismiss = { showChapters = false },
            onSelect = {
                showChapters = false
                onChapter(it)
            },
        )
    }
    if (showSettings) {
        ReaderSettingsSheet(
            settings = state.settings,
            onDismiss = { showSettings = false },
            onSettings = onSettings,
            showTextSettings = false,
        )
    }
    previewImageUri?.let { uri ->
        ImagePreviewOverlay(
            imageUri = uri,
            onDismiss = { previewImageUri = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfReaderScreen(
    state: ReaderUiState,
    onBack: () -> Unit,
    onProgress: (Int, Int) -> Unit,
    onEnsurePages: (Int) -> Unit,
    onChapter: (ReaderChapter) -> Unit,
    onJumpConsumed: () -> Unit,
) {
    val item = state.selectedItem
    var showChapters by remember { mutableStateOf(false) }
    var previewPageUri by remember { mutableStateOf<String?>(null) }
    var lastReportedIndex by remember(state.selectedItem?.id) { mutableStateOf(-1) }
    var restoreReady by remember(state.selectedItem?.id, state.pdfPageCount) { mutableStateOf(false) }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var showChrome by remember(isLandscape) { mutableStateOf(!isLandscape) }
    val canPrevious = state.pdfPageIndex > 0
    val canNext = state.pdfPageCount == 0 || state.pdfPageIndex < state.pdfPageCount - 1
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val currentIndex by remember {
        derivedStateOf { listState.firstVisibleItemIndex.coerceIn(0, (state.pdfPageCount - 1).coerceAtLeast(0)) }
    }

    LaunchedEffect(state.pdfPageCount, state.selectedItem?.id) {
        if (state.pdfPageCount > 0) {
            restoreReady = false
            val restoredIndex = state.currentIndex.coerceIn(0, state.pdfPageCount - 1)
            listState.scrollToItem(restoredIndex, state.pdfPageOffset)
            (restoredIndex - 2..restoredIndex + 8)
                .filter { it in 0 until state.pdfPageCount }
                .forEach(onEnsurePages)
            lastReportedIndex = restoredIndex
            restoreReady = true
        }
    }
    LaunchedEffect(state.jumpToIndex, state.pdfPageCount) {
        val target = state.jumpToIndex ?: return@LaunchedEffect
        if (state.pdfPageCount > 0) {
            val safeTarget = target.coerceIn(0, state.pdfPageCount - 1)
            listState.scrollToItem(safeTarget)
            lastReportedIndex = safeTarget
            onProgress(safeTarget, 0)
        }
        onJumpConsumed()
    }
    LaunchedEffect(listState, state.pdfPageCount, restoreReady) {
        if (state.pdfPageCount <= 0 || !restoreReady) return@LaunchedEffect
        snapshotFlow {
            listState.firstVisibleItemIndex.coerceIn(0, state.pdfPageCount - 1) to listState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                lastReportedIndex = index
                onProgress(index, offset)
            }
    }
    LaunchedEffect(listState, state.pdfPageCount) {
        if (state.pdfPageCount > 0) {
            snapshotFlow {
                val visible = listState.layoutInfo.visibleItemsInfo.map { it.index }
                if (visible.isEmpty()) {
                    currentIndex..currentIndex
                } else {
                    visible.minOrNull()!!..visible.maxOrNull()!!
                }
            }
                .distinctUntilChanged()
                .collect { range ->
                    val start = range.first.coerceIn(0, state.pdfPageCount - 1)
                    val end = range.last.coerceIn(start, state.pdfPageCount - 1)
                    (start - 2..end + 8)
                        .filter { it in 0 until state.pdfPageCount }
                        .forEach(onEnsurePages)
                }
        }
    }
    Scaffold(
        topBar = if (showChrome) {
            {
            TopAppBar(
                title = { Text(item?.title ?: "PDF") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showChapters = true }) {
                        Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "目录")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
            }
        } else {
            {}
        },
    ) { padding ->
        val error = state.errorMessage
        if (error != null) {
            ReaderError(modifier = Modifier.padding(padding), message = error, onBack = onBack)
        } else if (state.isLoading || state.pdfPageCount == 0) {
            LoadingReader(modifier = Modifier.padding(padding), text = "正在加载 PDF...")
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .clickable(enabled = isLandscape) { showChrome = !showChrome }
                    .pageTapNavigation(
                        onPrevious = {
                            if (canPrevious) {
                                val target = (state.pdfPageIndex - 1).coerceAtLeast(0)
                                lastReportedIndex = target
                                onProgress(target, 0)
                                scope.launch { listState.animateScrollToItem(target) }
                            }
                        },
                        onNext = {
                            if (canNext) {
                                val target = (state.pdfPageIndex + 1).coerceAtMost(state.pdfPageCount - 1)
                                lastReportedIndex = target
                                onProgress(target, 0)
                                scope.launch { listState.animateScrollToItem(target) }
                            }
                        },
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.pdfPageCount) { pageIndex ->
                    val pageUri = state.pdfPages[pageIndex]
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "${pageIndex + 1}/${state.pdfPageCount}",
                            modifier = Modifier.padding(vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (pageUri == null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("正在加载第 ${pageIndex + 1} 页...")
                            }
                        } else {
                            AsyncImage(
                                model = pageUri,
                                contentDescription = "${item?.title ?: "PDF"} ${pageIndex + 1}",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { previewPageUri = pageUri },
                                contentScale = ContentScale.FillWidth,
                            )
                        }
                    }
                }
            }
        }
    }
    if (showChapters) {
        ReaderChapterSheet(
            chapters = state.chapters,
            currentIndex = state.currentIndex,
            onDismiss = { showChapters = false },
            onSelect = {
                showChapters = false
                onChapter(it)
            },
        )
    }
    previewPageUri?.let { uri ->
        ImagePreviewOverlay(
            imageUri = uri,
            onDismiss = { previewPageUri = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopBar(
    title: String,
    background: Color,
    titleColor: Color = Color.Unspecified,
    iconColor: Color = Color.Unspecified,
    onBack: () -> Unit,
    onShowChapters: () -> Unit,
    onShowSettings: () -> Unit,
    extraActions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = iconColor)
            }
        },
        actions = {
            extraActions()
            IconButton(onClick = onShowChapters) {
                Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "目录", tint = iconColor)
            }
            IconButton(onClick = onShowSettings) {
                Icon(Icons.Default.Settings, contentDescription = "阅读设置", tint = iconColor)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = background,
            titleContentColor = titleColor,
        ),
    )
}

@Composable
private fun ReaderBlock(
    block: ReaderContentBlock,
    settings: ReaderSettings,
    textColor: Color,
    applyFirstLineIndent: Boolean = true,
) {
    when (block) {
        is ReaderContentBlock.Text -> Text(
            text = if (settings.firstLineIndent && applyFirstLineIndent) {
                block.text.lines().joinToString("\n") { line -> line.withFirstLineIndentIfNeeded() }
            } else {
                block.text
            },
            fontSize = settings.fontSizeSp.sp,
            lineHeight = (settings.fontSizeSp + 10).sp,
            color = textColor,
        )

        is ReaderContentBlock.Image -> AsyncImage(
            model = block.uri,
            contentDescription = block.name,
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth,
        )
    }
}

private data class MeasuredReaderPage(
    val block: ReaderContentBlock,
    val sourceIndex: Int,
)

private val readerPageCache = object : LinkedHashMap<String, List<MeasuredReaderPage>>(8, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<MeasuredReaderPage>>): Boolean {
        return size > 4
    }
}

private fun paginateBlocksForPageMode(
    blocks: List<ReaderContentBlock>,
    settings: ReaderSettings,
    pageWidthPx: Int,
    pageHeightPx: Int,
    density: Float,
): List<MeasuredReaderPage> {
    if (pageWidthPx <= 0 || pageHeightPx <= 0) {
        return blocks.mapIndexed { index, block -> MeasuredReaderPage(block, index) }
    }
    val cacheKey = buildReaderPageCacheKey(blocks, settings, pageWidthPx, pageHeightPx, density)
    readerPageCache[cacheKey]?.let { return it }
    val lineHeightPx = ((settings.fontSizeSp + 10) * density).toInt().coerceAtLeast(1)
    val linesPerPage = ((pageHeightPx - lineHeightPx / 2) / lineHeightPx).coerceAtLeast(1)
    val pages = blocks.flatMapIndexed { index, block ->
        when (block) {
            is ReaderContentBlock.Image -> listOf(MeasuredReaderPage(block, index))
            is ReaderContentBlock.Text -> paginateTextForPageMode(
                text = block.text,
                sourceIndex = index,
                settings = settings,
                pageWidthPx = pageWidthPx,
                linesPerPage = linesPerPage,
                density = density,
            )
        }
    }.ifEmpty {
        listOf(MeasuredReaderPage(ReaderContentBlock.Text(""), 0))
    }
    readerPageCache[cacheKey] = pages
    return pages
}

private fun paginateTextForPageMode(
    text: String,
    sourceIndex: Int,
    settings: ReaderSettings,
    pageWidthPx: Int,
    linesPerPage: Int,
    density: Float,
): List<MeasuredReaderPage> {
    val displayText = readerDisplayText(text, settings)
    val layout = buildReaderStaticLayout(displayText, settings, pageWidthPx, density)
    val pages = mutableListOf<MeasuredReaderPage>()
    var startLine = 0
    while (startLine < layout.lineCount) {
        val endLine = (startLine + linesPerPage).coerceAtMost(layout.lineCount)
        val start = layout.getLineStart(startLine)
        val next = layout.getLineStart(endLine).coerceAtLeast(start + 1)
        val pageText = displayText.substring(start, next).trimEnd()
        pages += MeasuredReaderPage(ReaderContentBlock.Text(pageText), sourceIndex)
        startLine = endLine
    }
    return pages.ifEmpty { listOf(MeasuredReaderPage(ReaderContentBlock.Text(displayText), sourceIndex)) }
}

private fun buildReaderPageCacheKey(
    blocks: List<ReaderContentBlock>,
    settings: ReaderSettings,
    pageWidthPx: Int,
    pageHeightPx: Int,
    density: Float,
): String {
    return listOf(
        System.identityHashCode(blocks),
        blocks.size,
        settings.fontSizeSp,
        settings.firstLineIndent,
        pageWidthPx,
        pageHeightPx,
        (density * 100).toInt(),
    ).joinToString(":")
}

private fun buildReaderStaticLayout(
    text: String,
    settings: ReaderSettings,
    pageWidthPx: Int,
    density: Float,
): StaticLayout {
    val lineHeightPx = ((settings.fontSizeSp + 10) * density).toInt().coerceAtLeast(1)
    val textPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        textSize = settings.fontSizeSp * density
    }
    return StaticLayout.Builder
        .obtain(text, 0, text.length, textPaint, pageWidthPx)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing((lineHeightPx - textPaint.fontSpacing).coerceAtLeast(0f), 1f)
        .setIncludePad(false)
        .build()
}

private fun readerDisplayText(text: String, settings: ReaderSettings): String {
    return if (settings.firstLineIndent) {
        text.lines().joinToString("\n") { line -> line.withFirstLineIndentIfNeeded() }
    } else {
        text
    }
}

private fun String.withFirstLineIndentIfNeeded(): String {
    if (isBlank()) return this
    return if (firstOrNull()?.isIndentChar() == true) this else "\u3000\u3000$this"
}

private fun Char.isIndentChar(): Boolean {
    return this == ' ' || this == '\t' || this == '\u3000'
}

@Composable
private fun ZoomableImageBox(
    resetToken: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(resetToken) {
        scale = 1f
        offset = Offset.Zero
    }
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
            .pointerInput(resetToken) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val nextScale = (scale * zoom).coerceIn(1f, 5f)
                    scale = nextScale
                    offset = if (nextScale == 1f) {
                        Offset.Zero
                    } else {
                        offset + pan * 2f
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun ImagePreviewOverlay(
    imageUri: String,
    onDismiss: () -> Unit,
) {
    var resetToken by remember(imageUri) { mutableStateOf(0) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pageTapNavigation(
                onPrevious = onDismiss,
                onNext = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        ZoomableImageBox(
            resetToken = resetToken,
            modifier = Modifier.fillMaxSize(),
        ) {
            AsyncImage(
                model = imageUri,
                contentDescription = "PDF preview",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        TextButton(
            onClick = { resetToken++ },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
        ) {
            Text("还原", color = Color.White)
        }
    }
}

private fun Modifier.pageTapNavigation(onPrevious: () -> Unit, onNext: () -> Unit): Modifier {
    return pointerInput(onPrevious, onNext) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val start = down.position
            val up = waitForUpOrCancellation()
            if (up != null) {
                val delta = up.position - start
                if (abs(delta.x) < 18f && abs(delta.y) < 18f) {
                    if (start.x < size.width / 2f) onPrevious() else onNext()
                } else if (abs(delta.x) > abs(delta.y) && abs(delta.x) > 60f) {
                    if (delta.x > 0f) onPrevious() else onNext()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderChapterSheet(
    chapters: List<ReaderChapter>,
    currentIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (ReaderChapter) -> Unit,
) {
    val listState = rememberLazyListState()
    val activeIndex = remember(chapters, currentIndex) {
        chapters.indexOfLast { it.targetIndex <= currentIndex }.coerceAtLeast(0)
    }
    LaunchedEffect(activeIndex) {
        if (chapters.isNotEmpty()) listState.scrollToItem(activeIndex)
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text("目录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            if (chapters.isEmpty()) {
                Text("暂未识别到目录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(chapters) { chapter ->
                        val selected = chapters.indexOf(chapter) == activeIndex
                        Text(
                            text = chapter.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { onSelect(chapter) }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSettingsSheet(
    settings: ReaderSettings,
    onDismiss: () -> Unit,
    onSettings: (ReaderSettings) -> Unit,
    showTextSettings: Boolean,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("阅读设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (showTextSettings) {
                SettingRow(title = "字体大小", value = "${settings.fontSizeSp}") {
                    TextButton(onClick = { onSettings(settings.copy(fontSizeSp = settings.fontSizeSp - 1)) }) { Text("A-") }
                    TextButton(onClick = { onSettings(settings.copy(fontSizeSp = settings.fontSizeSp + 1)) }) { Text("A+") }
                }
                SettingRow(title = "主题", value = themeLabel(settings.theme)) {
                    TextButton(onClick = { onSettings(settings.copy(theme = ReaderTheme.LIGHT)) }) { Text("浅色") }
                    TextButton(onClick = { onSettings(settings.copy(theme = ReaderTheme.SEPIA)) }) { Text("护眼") }
                    TextButton(onClick = { onSettings(settings.copy(theme = ReaderTheme.DARK)) }) { Text("深色") }
                }
                SettingRow(title = "首行缩进", value = if (settings.firstLineIndent) "开" else "关") {
                    TextButton(onClick = { onSettings(settings.copy(firstLineIndent = !settings.firstLineIndent)) }) {
                        Text(if (settings.firstLineIndent) "关闭" else "开启")
                    }
                }
            }
            SettingRow(title = "翻页方式", value = pageModeLabel(settings.pageMode)) {
                TextButton(onClick = { onSettings(settings.copy(pageMode = ReaderPageMode.SCROLL)) }) { Text("滚动") }
                TextButton(onClick = { onSettings(settings.copy(pageMode = ReaderPageMode.PAGE)) }) { Text("分页") }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SettingRow(title: String, value: String, controls: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            controls()
        }
    }
}

@Composable
private fun LoadingReader(modifier: Modifier = Modifier, text: String, dark: Boolean = false) {
    val background = if (dark) Color.Black else MaterialTheme.colorScheme.background
    val textColor = if (dark) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = textColor)
    }
}

@Composable
private fun ReaderError(modifier: Modifier = Modifier, message: String, onBack: () -> Unit, dark: Boolean = false) {
    val background = if (dark) Color.Black else MaterialTheme.colorScheme.background
    val textColor = if (dark) Color.White else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message, color = textColor, style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onBack) { Text("返回书架") }
        }
    }
}

private fun readerIcon(type: ReaderItemType): ImageVector {
    return when (type) {
        ReaderItemType.TEXT -> Icons.Default.TextFields
        ReaderItemType.EPUB -> Icons.Default.Book
        ReaderItemType.PDF -> Icons.Default.PictureAsPdf
        ReaderItemType.COMIC_FOLDER,
        ReaderItemType.COMIC_ARCHIVE -> Icons.Default.Image
    }
}

private fun readerThemeColors(theme: ReaderTheme): Pair<Color, Color> {
    return when (theme) {
        ReaderTheme.LIGHT -> Color(0xFFFFFBFE) to Color(0xFF1C1B1F)
        ReaderTheme.SEPIA -> Color(0xFFF3E7D0) to Color(0xFF2A2118)
        ReaderTheme.DARK -> Color(0xFF101010) to Color(0xFFE7E0DC)
    }
}

private fun themeLabel(theme: ReaderTheme): String {
    return when (theme) {
        ReaderTheme.LIGHT -> "浅色"
        ReaderTheme.SEPIA -> "护眼"
        ReaderTheme.DARK -> "深色"
    }
}

private fun pageModeLabel(mode: ReaderPageMode): String {
    return when (mode) {
        ReaderPageMode.SCROLL -> "滚动"
        ReaderPageMode.PAGE -> "分页"
    }
}
