package com.wode.app.service

import android.content.Context
import com.google.gson.Gson
import com.wode.app.data.ReaderItem
import com.wode.app.data.ReaderPageMode
import com.wode.app.data.ReaderSettings
import com.wode.app.data.ReaderTheme

class ReaderLibraryStore(context: Context) {
    private val prefs = context.getSharedPreferences("wode_reader", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getItems(): List<ReaderItem> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching { gson.fromJson(raw, Array<ReaderItem>::class.java).toList() }
            .getOrNull()
            .orEmpty()
            .sortedByDescending { it.lastOpenedAt }
    }

    fun addItems(items: List<ReaderItem>): List<ReaderItem> {
        val existing = getItems()
        val byUri = (existing + items).distinctBy { it.sourceUri }
        saveItems(byUri)
        return byUri
    }

    fun removeItem(id: String): List<ReaderItem> {
        val next = getItems().filterNot { it.id == id }
        saveItems(next)
        return next
    }

    fun renameItem(id: String, title: String): List<ReaderItem> {
        val safeTitle = title.trim()
        if (safeTitle.isBlank()) return getItems()
        val next = getItems().map { item ->
            if (item.id == id) item.copy(title = safeTitle) else item
        }
        saveItems(next)
        return next
    }

    fun resolveItem(item: ReaderItem): ReaderItem {
        val latest = getItems().firstOrNull { it.id == item.id || it.sourceUri == item.sourceUri } ?: item
        val savedIndex = getSavedIndex(latest)
        val savedProgress = prefs.getFloat(positionKey(latest.sourceUri, "progress"), latest.progress)
        val savedLabel = prefs.getString(positionKey(latest.sourceUri, "label"), latest.progressLabel).orEmpty()
        return latest.copy(
            progress = savedProgress,
            progressIndex = savedIndex,
            progressLabel = savedLabel,
        )
    }

    fun getSavedIndex(item: ReaderItem): Int {
        return prefs.getInt(positionKey(item.sourceUri, "index"), item.progressIndex).coerceAtLeast(0)
    }

    fun getSavedOffset(item: ReaderItem): Int {
        return prefs.getInt(positionKey(item.sourceUri, "offset"), 0).coerceAtLeast(0)
    }

    fun updateProgress(item: ReaderItem, progress: Float, label: String, index: Int = 0): ReaderItem? {
        savePosition(item.sourceUri, progress, label, index, 0)
        return updateProgress(item.id, progress, label, index)
    }

    fun updateProgress(item: ReaderItem, progress: Float, label: String, index: Int = 0, offset: Int): ReaderItem? {
        savePosition(item.sourceUri, progress, label, index, offset)
        return updateProgress(item.id, progress, label, index)
    }

    fun updateProgress(id: String, progress: Float, label: String, index: Int = 0): ReaderItem? {
        var updated: ReaderItem? = null
        val next = getItems().map { item ->
            if (item.id == id) {
                item.copy(
                    progress = progress.coerceIn(0f, 1f),
                    progressIndex = index.coerceAtLeast(0),
                    progressLabel = label,
                    lastOpenedAt = System.currentTimeMillis(),
                ).also { updated = it }
            } else {
                item
            }
        }
        saveItems(next)
        return updated
    }

    fun markOpened(id: String): ReaderItem? {
        var updated: ReaderItem? = null
        val next = getItems().map { item ->
            if (item.id == id) {
                item.copy(lastOpenedAt = System.currentTimeMillis()).also { updated = it }
            } else {
                item
            }
        }
        saveItems(next)
        return updated
    }

    fun getSettings(): ReaderSettings {
        val fontSize = prefs.getInt(KEY_FONT_SIZE, 18).coerceIn(12, 32)
        val themeName = prefs.getString(KEY_THEME, ReaderTheme.LIGHT.name)
        val theme = ReaderTheme.values().firstOrNull { it.name == themeName } ?: ReaderTheme.LIGHT
        val pageModeName = prefs.getString(KEY_PAGE_MODE, ReaderPageMode.SCROLL.name)
        val pageMode = ReaderPageMode.values().firstOrNull { it.name == pageModeName } ?: ReaderPageMode.SCROLL
        val firstLineIndent = prefs.getBoolean(KEY_FIRST_LINE_INDENT, true)
        return ReaderSettings(
            fontSizeSp = fontSize,
            theme = theme,
            pageMode = pageMode,
            firstLineIndent = firstLineIndent,
        )
    }

    fun saveSettings(settings: ReaderSettings) {
        prefs.edit()
            .putInt(KEY_FONT_SIZE, settings.fontSizeSp.coerceIn(12, 32))
            .putString(KEY_THEME, settings.theme.name)
            .putString(KEY_PAGE_MODE, settings.pageMode.name)
            .putBoolean(KEY_FIRST_LINE_INDENT, settings.firstLineIndent)
            .apply()
    }

    private fun saveItems(items: List<ReaderItem>) {
        prefs.edit().putString(KEY_ITEMS, gson.toJson(items)).apply()
    }

    private fun savePosition(sourceUri: String, progress: Float, label: String, index: Int, offset: Int) {
        prefs.edit()
            .putInt(positionKey(sourceUri, "index"), index.coerceAtLeast(0))
            .putInt(positionKey(sourceUri, "offset"), offset.coerceAtLeast(0))
            .putFloat(positionKey(sourceUri, "progress"), progress.coerceIn(0f, 1f))
            .putString(positionKey(sourceUri, "label"), label)
            .apply()
    }

    private fun positionKey(sourceUri: String, name: String): String {
        return "reader_position_${sourceUri.hashCode()}_$name"
    }

    companion object {
        private const val KEY_ITEMS = "reader_items"
        private const val KEY_FONT_SIZE = "reader_font_size"
        private const val KEY_THEME = "reader_theme"
        private const val KEY_PAGE_MODE = "reader_page_mode"
        private const val KEY_FIRST_LINE_INDENT = "reader_first_line_indent"
    }
}
