package com.wode.app.service

import android.content.Context
import android.net.Uri
import com.wode.app.data.MusicSource
import com.wode.app.data.MusicTrack
import com.wode.app.viewmodel.MusicSortMode
import org.json.JSONArray
import org.json.JSONObject

class MusicStore(context: Context) {
    private val prefs = context.getSharedPreferences("wode_music", Context.MODE_PRIVATE)

    fun getFolderUris(): List<Uri> {
        return prefs.getStringSet(KEY_FOLDER_URIS, emptySet())
            .orEmpty()
            .mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
    }

    fun addFolderUri(uri: Uri) {
        val values = prefs.getStringSet(KEY_FOLDER_URIS, emptySet()).orEmpty().toMutableSet()
        values += uri.toString()
        prefs.edit().putStringSet(KEY_FOLDER_URIS, values).apply()
    }

    fun removeFolderUri(uri: Uri) {
        val values = prefs.getStringSet(KEY_FOLDER_URIS, emptySet()).orEmpty().toMutableSet()
        values -= uri.toString()
        prefs.edit().putStringSet(KEY_FOLDER_URIS, values).apply()
    }

    fun isSystemLibraryEnabled(): Boolean {
        return prefs.getBoolean(KEY_SYSTEM_LIBRARY_ENABLED, false)
    }

    fun setSystemLibraryEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SYSTEM_LIBRARY_ENABLED, enabled).apply()
    }

    fun getSortMode(): MusicSortMode {
        val raw = prefs.getString(KEY_SORT_MODE, MusicSortMode.NAME_ASC.name)
        return MusicSortMode.values().firstOrNull { it.name == raw } ?: MusicSortMode.NAME_ASC
    }

    fun setSortMode(sortMode: MusicSortMode) {
        prefs.edit().putString(KEY_SORT_MODE, sortMode.name).apply()
    }

    fun getCachedFolderTracks(folderUris: List<Uri>): List<MusicTrack> {
        val allowedRoots = folderUris.map { it.toString() }.toSet()
        return runCatching {
            val raw = prefs.getString(KEY_FOLDER_TRACK_CACHE, null) ?: return emptyList()
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val folderRootUri = item.optString("folderRootUri").takeIf { it.isNotBlank() } ?: continue
                    if (folderRootUri !in allowedRoots) continue
                    add(
                        MusicTrack(
                            id = item.optString("id"),
                            title = item.optString("title"),
                            artist = item.optString("artist"),
                            album = item.optString("album"),
                            durationMs = item.optLong("durationMs"),
                            modifiedTimeMs = item.optLong("modifiedTimeMs"),
                            uri = Uri.parse(item.optString("uri")),
                            source = MusicSource.FOLDER,
                            folderName = item.optString("folderName").takeIf { it.isNotBlank() },
                            folderRootUri = folderRootUri,
                            lyricUri = item.optString("lyricUri").takeIf { it.isNotBlank() }?.let(Uri::parse),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveCachedFolderTracks(tracks: List<MusicTrack>) {
        val array = JSONArray()
        tracks
            .filter { it.source == MusicSource.FOLDER }
            .forEach { track ->
                array.put(
                    JSONObject()
                        .put("id", track.id)
                        .put("title", track.title)
                        .put("artist", track.artist)
                        .put("album", track.album)
                        .put("durationMs", track.durationMs)
                        .put("modifiedTimeMs", track.modifiedTimeMs)
                        .put("uri", track.uri.toString())
                        .put("folderName", track.folderName.orEmpty())
                        .put("folderRootUri", track.folderRootUri.orEmpty())
                        .put("lyricUri", track.lyricUri?.toString().orEmpty()),
                )
            }
        prefs.edit().putString(KEY_FOLDER_TRACK_CACHE, array.toString()).apply()
    }

    fun removeCachedTracksForFolder(uri: Uri) {
        val folderRoot = uri.toString()
        saveCachedFolderTracks(getCachedFolderTracks(getFolderUris()).filterNot { it.folderRootUri == folderRoot })
    }

    companion object {
        private const val KEY_FOLDER_URIS = "folder_uris"
        private const val KEY_SYSTEM_LIBRARY_ENABLED = "system_library_enabled"
        private const val KEY_FOLDER_TRACK_CACHE = "folder_track_cache"
        private const val KEY_SORT_MODE = "sort_mode"
    }
}
