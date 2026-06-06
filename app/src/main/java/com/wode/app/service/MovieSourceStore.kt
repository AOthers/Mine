package com.wode.app.service

import android.content.Context
import com.google.gson.Gson
import com.wode.app.data.MovieSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class MovieSourceStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _sources = MutableStateFlow(loadSources())
    val sources: StateFlow<List<MovieSource>> = _sources.asStateFlow()

    private val _currentSource = MutableStateFlow(resolveCurrentSource(_sources.value))
    val currentSource: StateFlow<MovieSource> = _currentSource.asStateFlow()

    fun addSource(name: String, url: String): MovieSource {
        val normalizedUrl = normalizeUrl(url)
        _sources.value.firstOrNull { it.url == normalizedUrl }?.let { existing ->
            return existing
        }
        val source = MovieSource(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "自定义来源" },
            url = normalizedUrl,
        )
        saveSources(_sources.value + source)
        return source
    }

    fun deleteSource(id: String) {
        if (id == DEFAULT_SOURCE_ID) return
        val nextSources = _sources.value
            .filterNot { it.id == id }
            .ifEmpty { listOf(DEFAULT_SOURCE) }
        saveSources(nextSources)
        if (_currentSource.value.id == id) {
            selectSource(DEFAULT_SOURCE_ID)
        }
    }

    fun selectSource(id: String) {
        val source = _sources.value.firstOrNull { it.id == id } ?: DEFAULT_SOURCE
        prefs.edit().putString(KEY_CURRENT_SOURCE_ID, source.id).apply()
        _currentSource.value = source
    }

    fun restoreDefaults() {
        prefs.edit()
            .remove(KEY_SOURCES)
            .putString(KEY_CURRENT_SOURCE_ID, DEFAULT_SOURCE.id)
            .apply()
        _sources.value = listOf(DEFAULT_SOURCE)
        _currentSource.value = DEFAULT_SOURCE
    }

    private fun loadSources(): List<MovieSource> {
        val raw = prefs.getString(KEY_SOURCES, null) ?: return listOf(DEFAULT_SOURCE)
        val saved = runCatching { gson.fromJson(raw, Array<MovieSource>::class.java).toList() }
            .getOrNull()
            .orEmpty()
            .mapNotNull { source ->
                val normalizedUrl = runCatching { normalizeUrl(source.url) }.getOrNull() ?: return@mapNotNull null
                MovieSource(
                    id = source.id.ifBlank { UUID.randomUUID().toString() },
                    name = source.name.ifBlank { "自定义来源" },
                    url = normalizedUrl,
                )
            }
        return (listOf(DEFAULT_SOURCE) + saved)
            .distinctBy { it.url }
            .ifEmpty { listOf(DEFAULT_SOURCE) }
    }

    private fun saveSources(sources: List<MovieSource>) {
        prefs.edit().putString(KEY_SOURCES, gson.toJson(sources)).apply()
        _sources.value = sources
    }

    private fun resolveCurrentSource(sources: List<MovieSource>): MovieSource {
        val currentId = prefs.getString(KEY_CURRENT_SOURCE_ID, DEFAULT_SOURCE.id)
        return sources.firstOrNull { it.id == currentId } ?: sources.firstOrNull() ?: DEFAULT_SOURCE
    }

    companion object {
        private const val PREFS_NAME = "movie_source_settings"
        private const val KEY_SOURCES = "movie_sources"
        private const val KEY_CURRENT_SOURCE_ID = "current_movie_source_id"

        const val DEFAULT_SOURCE_ID = "default_hhkan"
        const val DEFAULT_SOURCE_NAME = "默认来源"
        const val DEFAULT_SOURCE_URL = "https://www.hhkan0.com/"

        val DEFAULT_SOURCE = MovieSource(
            id = DEFAULT_SOURCE_ID,
            name = DEFAULT_SOURCE_NAME,
            url = DEFAULT_SOURCE_URL,
        )

        fun normalizeUrl(url: String): String {
            val trimmed = url.trim()
            return when {
                trimmed.isBlank() -> DEFAULT_SOURCE_URL
                trimmed.startsWith("http://", ignoreCase = true) ||
                    trimmed.startsWith("https://", ignoreCase = true) -> trimmed
                else -> "https://$trimmed"
            }
        }
    }
}
