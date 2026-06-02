package com.wode.app.service

import com.wode.app.data.MusicTrack
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class OnlineLyricsService {
    private val client = OkHttpClient.Builder()
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    fun searchLyrics(track: MusicTrack): String? {
        val query = buildQuery(track)
        if (query.trackName.isBlank()) return null

        val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", query.trackName)
            .apply {
                if (query.artistName.isNotBlank()) addQueryParameter("artist_name", query.artistName)
                if (track.durationMs > 0L) addQueryParameter("duration", (track.durationMs / 1000).toString())
            }
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "WodeApp/1.0")
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                pickBestLyrics(JSONArray(body), query, track.durationMs)
            }
        }.getOrNull()
    }

    private fun pickBestLyrics(results: JSONArray, query: LyricsQuery, durationMs: Long): String? {
        var fallback: String? = null
        for (index in 0 until results.length()) {
            val item = results.optJSONObject(index) ?: continue
            val syncedLyrics = item.cleanString("syncedLyrics")
            val plainLyrics = item.cleanString("plainLyrics")
            val lyrics = syncedLyrics ?: plainLyrics ?: continue
            if (fallback == null) fallback = lyrics

            val titleMatches = item.optString("trackName").sameTextAs(query.trackName)
            val artistMatches = query.artistName.isBlank() || item.optString("artistName").sameTextAs(query.artistName)
            val durationMatches = durationMs <= 0L || abs(item.optLong("duration") - durationMs / 1000) <= 3
            if (titleMatches && artistMatches && durationMatches) return lyrics
        }
        return fallback
    }

    private fun buildQuery(track: MusicTrack): LyricsQuery {
        val rawTitle = cleanText(track.title)
        var artist = cleanText(track.artist).takeUnless { it.isUnknownArtist() }.orEmpty()
        var title = rawTitle
        val parts = rawTitle.split("-", "\u2013", "\u2014")
            .map { cleanText(it) }
            .filter { it.isNotBlank() }

        if (artist.isBlank() && parts.size >= 2) {
            title = parts.first()
            artist = parts.drop(1).firstOrNull { !it.isMostlyNumber() }.orEmpty()
        } else if (parts.size >= 2 && parts.last().isMostlyNumber()) {
            title = parts.dropLast(1).joinToString("-")
        }

        return LyricsQuery(trackName = title, artistName = artist)
    }

    private fun cleanText(value: String): String {
        return value
            .replace(Regex("\\.(mp3|m4a|aac|flac|ogg|opus|wav)$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun String.isUnknownArtist(): Boolean {
        val normalized = lowercase(Locale.getDefault())
        return isBlank() || this == "\u672a\u77e5\u6b4c\u624b" || normalized == "unknown artist"
    }

    private fun String.isMostlyNumber(): Boolean {
        val digits = count { it.isDigit() }
        return isNotBlank() && digits >= length * 0.8
    }

    private fun String.sameTextAs(other: String): Boolean {
        return cleanComparable(this) == cleanComparable(other)
    }

    private fun cleanComparable(value: String): String {
        return value.lowercase(Locale.getDefault())
            .replace(Regex("[\\s_\\-\\u2013\\u2014]+"), "")
            .trim()
    }

    private fun org.json.JSONObject.cleanString(name: String): String? {
        if (isNull(name)) return null
        return optString(name).trim().takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
    }

    private data class LyricsQuery(
        val trackName: String,
        val artistName: String,
    )
}
