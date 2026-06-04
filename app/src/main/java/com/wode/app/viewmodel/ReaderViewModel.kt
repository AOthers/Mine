package com.wode.app.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wode.app.data.ComicImage
import com.wode.app.data.ReaderChapter
import com.wode.app.data.ReaderContentBlock
import com.wode.app.data.ReaderItem
import com.wode.app.data.ReaderItemType
import com.wode.app.data.ReaderSettings
import com.wode.app.data.TextContent
import com.wode.app.service.ComicReaderService
import com.wode.app.service.EpubReaderService
import com.wode.app.service.PdfReaderService
import com.wode.app.service.ReaderImportService
import com.wode.app.service.ReaderLibraryStore
import com.wode.app.service.TextReaderService
import java.util.zip.ZipException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReaderUiState(
    val items: List<ReaderItem> = emptyList(),
    val selectedItem: ReaderItem? = null,
    val textContent: TextContent? = null,
    val comicImages: List<ComicImage> = emptyList(),
    val pdfPage: Bitmap? = null,
    val pdfPages: Map<Int, String> = emptyMap(),
    val pdfPageIndex: Int = 0,
    val pdfPageOffset: Int = 0,
    val pdfPageCount: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val chapters: List<ReaderChapter> = emptyList(),
    val currentIndex: Int = 0,
    val jumpToIndex: Int? = null,
    val settings: ReaderSettings = ReaderSettings(),
)

sealed class ReaderEvent {
    data class Toast(val message: String) : ReaderEvent()
}

class ReaderViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ReaderLibraryStore(application)
    private val importer = ReaderImportService(application)
    private val textReader = TextReaderService(application)
    private val epubReader = EpubReaderService(application)
    private val comicReader = ComicReaderService(application)
    private val pdfReader = PdfReaderService(application)
    private val loadingPdfPages = mutableSetOf<Int>()

    private val _uiState = MutableStateFlow(
        ReaderUiState(
            items = store.getItems(),
            settings = store.getSettings(),
        ),
    )
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ReaderEvent>()
    val events: SharedFlow<ReaderEvent> = _events.asSharedFlow()

    fun addFile(uri: Uri) {
        viewModelScope.launch {
            importer.importFile(uri)
                .onSuccess { addImportedItems(it) }
                .onFailure { emitToast(it.message ?: "导入失败") }
        }
    }

    fun addFolder(uri: Uri) {
        viewModelScope.launch {
            importer.importFolder(uri)
                .onSuccess { addImportedItems(it) }
                .onFailure { emitToast(it.message ?: "导入失败") }
        }
    }

    fun removeItem(item: ReaderItem) {
        _uiState.value = _uiState.value.copy(items = store.removeItem(item.id))
    }

    fun renameItem(item: ReaderItem, title: String) {
        _uiState.value = _uiState.value.copy(items = store.renameItem(item.id, title))
    }

    fun openItem(item: ReaderItem) {
        val latestItem = store.resolveItem(item)
        _uiState.value = _uiState.value.copy(
            selectedItem = latestItem,
            textContent = null,
            comicImages = emptyList(),
            pdfPage = null,
            pdfPages = emptyMap(),
            isLoading = true,
            errorMessage = null,
            chapters = emptyList(),
            currentIndex = latestItem.progressIndex.coerceAtLeast(0),
            jumpToIndex = null,
        )
        viewModelScope.launch {
            when (latestItem.type) {
                ReaderItemType.TEXT -> loadText(latestItem)
                ReaderItemType.EPUB -> loadEpub(latestItem)
                ReaderItemType.PDF -> loadPdf(latestItem, store.getSavedIndex(latestItem))
                ReaderItemType.COMIC_FOLDER -> loadComicFolder(latestItem)
                ReaderItemType.COMIC_ARCHIVE -> loadComicArchive(latestItem)
            }
            store.markOpened(latestItem.id)
            _uiState.value = _uiState.value.copy(items = store.getItems())
        }
    }

    fun closeReader() {
        _uiState.value = _uiState.value.copy(
            selectedItem = null,
            textContent = null,
            comicImages = emptyList(),
            pdfPage = null,
            pdfPages = emptyMap(),
            pdfPageIndex = 0,
            pdfPageOffset = 0,
            pdfPageCount = 0,
            isLoading = false,
            errorMessage = null,
            chapters = emptyList(),
            currentIndex = 0,
            jumpToIndex = null,
        )
    }

    fun updateTextProgress(index: Int, total: Int) {
        val item = _uiState.value.selectedItem ?: return
        val safeTotal = total.coerceAtLeast(1)
        val safeIndex = index.coerceIn(0, safeTotal - 1)
        val progress = (safeIndex + 1).toFloat() / safeTotal
        _uiState.value = _uiState.value.copy(currentIndex = safeIndex)
        store.updateProgress(item, progress, "${safeIndex + 1}/$safeTotal", safeIndex)?.let { refreshItem(it) }
    }

    fun updateComicProgress(index: Int) {
        val item = _uiState.value.selectedItem ?: return
        val total = _uiState.value.comicImages.size.coerceAtLeast(1)
        val safeIndex = index.coerceIn(0, total - 1)
        val progress = (safeIndex + 1).toFloat() / total
        _uiState.value = _uiState.value.copy(currentIndex = safeIndex)
        store.updateProgress(item, progress, "${safeIndex + 1}/$total", safeIndex)?.let { refreshItem(it) }
    }

    fun showPdfPage(index: Int) {
        val item = _uiState.value.selectedItem ?: return
        if (item.type != ReaderItemType.PDF) return
        val safeIndex = index.coerceIn(0, (_uiState.value.pdfPageCount - 1).coerceAtLeast(0))
        _uiState.value = _uiState.value.copy(jumpToIndex = safeIndex)
        updatePdfProgress(safeIndex)
    }

    fun updatePdfProgress(index: Int) {
        updatePdfProgress(index, 0)
    }

    fun updatePdfProgress(index: Int, offset: Int) {
        val item = _uiState.value.selectedItem ?: return
        if (item.type != ReaderItemType.PDF) return
        val total = _uiState.value.pdfPageCount.coerceAtLeast(1)
        val safeIndex = index.coerceIn(0, total - 1)
        val safeOffset = offset.coerceAtLeast(0)
        val progress = (safeIndex + 1).toFloat() / total
        _uiState.value = _uiState.value.copy(pdfPageIndex = safeIndex, pdfPageOffset = safeOffset, currentIndex = safeIndex)
        store.updateProgress(item, progress, "${safeIndex + 1}/$total", safeIndex, safeOffset)?.let { refreshItem(it) }
        ensurePdfPages(item, safeIndex)
    }

    fun ensurePdfPagesAround(index: Int) {
        val item = _uiState.value.selectedItem ?: return
        if (item.type != ReaderItemType.PDF) return
        ensurePdfPages(item, index)
    }

    fun updateSettings(settings: ReaderSettings) {
        val safe = settings.copy(fontSizeSp = settings.fontSizeSp.coerceIn(12, 32))
        store.saveSettings(safe)
        _uiState.value = _uiState.value.copy(settings = safe)
    }

    fun jumpToChapter(chapter: ReaderChapter) {
        val item = _uiState.value.selectedItem ?: return
        when (item.type) {
            ReaderItemType.PDF -> showPdfPage(chapter.targetIndex)
            else -> _uiState.value = _uiState.value.copy(jumpToIndex = chapter.targetIndex)
        }
    }

    fun consumeJumpRequest() {
            _uiState.value = _uiState.value.copy(jumpToIndex = null)
    }

    private fun addImportedItems(items: List<ReaderItem>) {
        val next = store.addItems(items)
        _uiState.value = _uiState.value.copy(items = next)
        emitToast("已添加 ${items.size} 个阅读项目")
    }

    private suspend fun loadText(item: ReaderItem) {
        val result = withContext(Dispatchers.IO) { textReader.load(Uri.parse(item.sourceUri), item.title) }
        result.onSuccess { content ->
            val blocks = paginateTextBlocks(content.blocks)
            val initialIndex = savedIndex(item, blocks.size)
            _uiState.value = _uiState.value.copy(
                textContent = content.copy(blocks = blocks),
                chapters = buildTextChapters(blocks, content.chapters),
                isLoading = false,
                errorMessage = null,
                currentIndex = initialIndex,
                jumpToIndex = null,
            )
        }.onFailure {
            showReaderError(it, "文本加载失败")
        }
    }

    private suspend fun loadEpub(item: ReaderItem) {
        val result = withContext(Dispatchers.IO) { epubReader.load(Uri.parse(item.sourceUri), item.title) }
        result.onSuccess { content ->
            val blocks = paginateTextBlocks(content.blocks)
            val initialIndex = savedIndex(item, blocks.size)
            _uiState.value = _uiState.value.copy(
                textContent = content.copy(blocks = blocks),
                chapters = buildTextChapters(blocks, content.chapters),
                isLoading = false,
                errorMessage = null,
                currentIndex = initialIndex,
                jumpToIndex = null,
            )
        }.onFailure {
            showReaderError(it, "EPUB 加载失败")
        }
    }

    private suspend fun loadComicFolder(item: ReaderItem) {
        val result = withContext(Dispatchers.IO) { comicReader.listFolderImages(Uri.parse(item.sourceUri)) }
        result.onSuccess {
            val initialIndex = savedIndex(item, it.size)
            _uiState.value = _uiState.value.copy(
                comicImages = it,
                chapters = buildImageChapters(it),
                isLoading = false,
                errorMessage = null,
                currentIndex = initialIndex,
                jumpToIndex = null,
            )
        }.onFailure {
            showReaderError(it, "漫画加载失败")
        }
    }

    private suspend fun loadComicArchive(item: ReaderItem) {
        val result = withContext(Dispatchers.IO) { comicReader.extractArchiveImages(Uri.parse(item.sourceUri), item.id) }
        result.onSuccess {
            val initialIndex = savedIndex(item, it.size)
            _uiState.value = _uiState.value.copy(
                comicImages = it,
                chapters = buildImageChapters(it),
                isLoading = false,
                errorMessage = null,
                currentIndex = initialIndex,
                jumpToIndex = null,
            )
        }.onFailure {
            showReaderError(it, "漫画压缩包加载失败")
        }
    }

    private suspend fun loadPdf(item: ReaderItem, pageIndex: Int) {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val uri = Uri.parse(item.sourceUri)
            val count = withContext(Dispatchers.IO) { pdfReader.getPageCount(uri) }.getOrElse {
                showReaderError(it, "PDF 加载失败")
                return
            }
            val safeIndex = pageIndex.coerceIn(0, count - 1)
            val savedOffset = store.getSavedOffset(item)
            if (false) withContext(Dispatchers.IO) { pdfReader.renderPage(uri, safeIndex) }.getOrElse {
                showReaderError(it, "PDF 渲染失败")
                return
            }
            _uiState.value = _uiState.value.copy(
                pdfPage = null,
                pdfPages = emptyMap(),
                pdfPageIndex = safeIndex,
                pdfPageOffset = savedOffset,
                pdfPageCount = count,
                chapters = buildPageChapters(count),
                currentIndex = safeIndex,
                jumpToIndex = null,
                isLoading = false,
                errorMessage = null,
            )
            ensurePdfPages(item, safeIndex)
    }

    private fun ensurePdfPages(item: ReaderItem, centerIndex: Int) {
        val count = _uiState.value.pdfPageCount
        if (count <= 0) return
        val wanted = (centerIndex - 4..centerIndex + 12)
            .filter { it in 0 until count }
        wanted.forEach { pageIndex ->
            if (_uiState.value.pdfPages.containsKey(pageIndex) || !loadingPdfPages.add(pageIndex)) return@forEach
            viewModelScope.launch {
                val pageUri = withContext(Dispatchers.IO) {
                    pdfReader.renderPageToCache(Uri.parse(item.sourceUri), item.id, pageIndex, targetWidth = 1800)
                }.getOrElse {
                    loadingPdfPages.remove(pageIndex)
                    showReaderError(it, "PDF 渲染失败")
                    return@launch
                }
                loadingPdfPages.remove(pageIndex)
                _uiState.value = _uiState.value.copy(
                    pdfPages = _uiState.value.pdfPages + (pageIndex to pageUri),
                )
            }
        }
    }

    private fun showReaderError(error: Throwable, fallback: String) {
        val message = friendlyReaderError(error, fallback)
        _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = message)
        emitToast(message)
    }

    private fun friendlyReaderError(error: Throwable, fallback: String): String {
        val raw = error.message.orEmpty()
        return when {
            error is ZipException && raw.contains("CRC", ignoreCase = true) ->
                "文件校验失败，可能是文件损坏或下载不完整"
            raw.contains("CRC", ignoreCase = true) ->
                "文件校验失败，可能是文件损坏或下载不完整"
            raw.isNotBlank() -> raw
            else -> fallback
        }
    }

    private fun refreshItem(item: ReaderItem) {
        _uiState.value = _uiState.value.copy(
            items = store.getItems(),
            selectedItem = item,
        )
    }

    private fun paginateTextBlocks(blocks: List<ReaderContentBlock>): List<ReaderContentBlock> {
        return blocks.flatMap { block ->
            when (block) {
                is ReaderContentBlock.Image -> listOf(block)
                is ReaderContentBlock.Text -> block.text
                    .split('\n')
                    .chunked(12)
                    .map { ReaderContentBlock.Text(it.joinToString("\n")) }
            }
        }
    }

    private fun progressToIndex(progress: Float, total: Int): Int {
        if (total <= 0 || progress <= 0f) return 0
        return ((progress * total).toInt() - 1).coerceIn(0, total - 1)
    }

    private fun savedIndex(item: ReaderItem, total: Int): Int {
        if (total <= 0) return 0
        return if (item.progressIndex > 0) {
            item.progressIndex.coerceIn(0, total - 1)
        } else {
            progressToIndex(item.progress, total)
        }
    }

    private fun buildTextChapters(
        blocks: List<ReaderContentBlock>,
        preferredChapters: List<ReaderChapter> = emptyList(),
    ): List<ReaderChapter> {
        if (preferredChapters.isNotEmpty()) {
            return preferredChapters.map { chapter ->
                chapter.copy(targetIndex = chapter.targetIndex.coerceIn(0, (blocks.size - 1).coerceAtLeast(0)))
            }
        }
        val pattern = Regex(
            """^\s*(序章|楔子|引子|前言|后记|尾声|第\s*[一二三四五六七八九十百千万零〇两\d]+\s*[章节回卷部篇集]|Chapter\s+\d+|\d+[\.、]\s*.+)""",
            RegexOption.IGNORE_CASE,
        )
        val chapters = blocks.mapIndexedNotNull { index, block ->
            val text = (block as? ReaderContentBlock.Text)?.text ?: return@mapIndexedNotNull null
            val title = text.lineSequence().firstOrNull { pattern.containsMatchIn(it) }?.trim()
            title?.let { ReaderChapter(it.take(60), index) }
        }.distinctBy { it.targetIndex }
        return chapters.ifEmpty {
            val step = (blocks.size / 12).coerceAtLeast(1)
            blocks.indices.step(step)
                .mapIndexed { chapterIndex, targetIndex -> ReaderChapter("第 ${chapterIndex + 1} 节", targetIndex) }
                .take(80)
        }
    }

    private fun buildImageChapters(images: List<ComicImage>): List<ReaderChapter> {
        return images.mapIndexed { index, image ->
            ReaderChapter("第 ${index + 1} 张 ${image.name}", index)
        }
    }

    private fun buildPageChapters(count: Int): List<ReaderChapter> {
        return (0 until count.coerceAtLeast(0)).map { index ->
            ReaderChapter("第 ${index + 1} 页", index)
        }
    }

    private fun emitToast(message: String) {
        viewModelScope.launch {
            _events.emit(ReaderEvent.Toast(message))
        }
    }
}
