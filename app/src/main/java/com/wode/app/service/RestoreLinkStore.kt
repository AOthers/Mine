package com.wode.app.service

import android.content.Context

class RestoreLinkStore(context: Context) {
    private val prefs = context.getSharedPreferences("restore_links", Context.MODE_PRIVATE)

    fun getLink(packageName: String): String {
        return prefs.getString(packageName, "") ?: ""
    }

    fun saveLink(packageName: String, link: String) {
        prefs.edit().putString(packageName, link.trim()).apply()
    }
}
