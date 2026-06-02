package com.wode.app.service

import android.content.Context
import android.net.Uri

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

    companion object {
        private const val KEY_FOLDER_URIS = "folder_uris"
    }
}
