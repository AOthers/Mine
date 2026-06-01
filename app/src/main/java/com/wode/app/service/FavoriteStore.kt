package com.wode.app.service

import android.content.Context

class FavoriteStore(context: Context) {
    private val prefs = context.getSharedPreferences("wode_favorites", Context.MODE_PRIVATE)

    fun isFavorite(toolId: String): Boolean {
        return prefs.getBoolean(toolId, false)
    }

    fun setFavorite(toolId: String, favorite: Boolean) {
        prefs.edit().putBoolean(toolId, favorite).apply()
    }

    companion object {
        const val TOOL_BACKUP_RESTORE = "backup_restore"
        const val TOOL_MOVIES = "movies"
    }
}
