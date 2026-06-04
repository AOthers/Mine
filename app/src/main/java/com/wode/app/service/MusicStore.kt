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

    fun applyTrackRenames(tracks: List<MusicTrack>): List<MusicTrack> {
        val renames = getTrackRenames()
        return tracks.filterNot { isTrackHidden(it) }.map { track ->
            val rename = renames[track.renameKey()]
            if (rename == null) {
                track
            } else {
                track.copy(
                    title = rename.title.ifBlank { track.title },
                    artist = rename.artist.ifBlank { track.artist },
                )
            }
        }
    }

    fun renameTrack(track: MusicTrack, title: String, artist: String) {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return
        val renames = getTrackRenames().toMutableMap()
        renames[track.renameKey()] = TrackRename(title = cleanTitle, artist = artist.trim())
        val json = JSONObject()
        renames.forEach { (key, value) ->
            if (key.isNotBlank() && value.title.isNotBlank()) {
                json.put(
                    key,
                    JSONObject()
                        .put("title", value.title)
                        .put("artist", value.artist),
                )
            }
        }
        prefs.edit().putString(KEY_TRACK_RENAMES, json.toString()).apply()
    }

    fun hideTrack(track: MusicTrack) {
        val hiddenTracks = prefs.getStringSet(KEY_HIDDEN_TRACKS, emptySet()).orEmpty().toMutableSet()
        hiddenTracks += track.renameKey()
        prefs.edit().putStringSet(KEY_HIDDEN_TRACKS, hiddenTracks).apply()
    }

    fun getCachedFolderTracks(folderUris: List<Uri>): List<MusicTrack> {
        val allowedRoots = folderUris.map { it.toString() }.toSet()
        return runCatching {
            val raw = prefs.getString(KEY_FOLDER_TRACK_CACHE, null) ?: return emptyList()
            val root = JSONObject(raw)
            if (root.optInt("version") != FOLDER_TRACK_CACHE_VERSION) return emptyList()
            val array = root.optJSONArray("tracks") ?: return emptyList()
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
                            parentUri = item.optString("parentUri").takeIf { it.isNotBlank() }?.let(Uri::parse),
                            fileName = item.optString("fileName").takeIf { it.isNotBlank() },
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
                        .put("parentUri", track.parentUri?.toString().orEmpty())
                        .put("fileName", track.fileName.orEmpty())
                        .put("lyricUri", track.lyricUri?.toString().orEmpty()),
                )
            }
        prefs.edit()
            .putString(
                KEY_FOLDER_TRACK_CACHE,
                JSONObject()
                    .put("version", FOLDER_TRACK_CACHE_VERSION)
                    .put("tracks", array)
                    .toString(),
            )
            .apply()
    }

    fun removeCachedTracksForFolder(uri: Uri) {
        val folderRoot = uri.toString()
        saveCachedFolderTracks(getCachedFolderTracks(getFolderUris()).filterNot { it.folderRootUri == folderRoot })
    }

    private fun getTrackRenames(): Map<String, TrackRename> {
        return runCatching {
            val raw = prefs.getString(KEY_TRACK_RENAMES, null) ?: return emptyMap()
            val json = JSONObject(raw)
            buildMap {
                json.keys().forEach { key ->
                    val value = json.opt(key)
                    val rename = when (value) {
                        is JSONObject -> TrackRename(
                            title = value.optString("title").trim(),
                            artist = value.optString("artist").trim(),
                        )
                        is String -> TrackRename(title = value.trim(), artist = "")
                        else -> null
                    }
                    if (key.isNotBlank() && rename != null && rename.title.isNotBlank()) put(key, rename)
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun MusicTrack.renameKey(): String = uri.toString()

    private fun isTrackHidden(track: MusicTrack): Boolean {
        return track.renameKey() in prefs.getStringSet(KEY_HIDDEN_TRACKS, emptySet()).orEmpty()
    }

    private data class TrackRename(
        val title: String,
        val artist: String,
    )

    companion object {
        private const val KEY_FOLDER_URIS = "folder_uris"
        private const val KEY_SYSTEM_LIBRARY_ENABLED = "system_library_enabled"
        private const val KEY_FOLDER_TRACK_CACHE = "folder_track_cache"
        private const val KEY_SORT_MODE = "sort_mode"
        private const val KEY_TRACK_RENAMES = "track_renames"
        private const val KEY_HIDDEN_TRACKS = "hidden_tracks"
        private const val FOLDER_TRACK_CACHE_VERSION = 2
    }
}
