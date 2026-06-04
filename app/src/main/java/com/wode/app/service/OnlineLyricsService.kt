package com.wode.app.service

import android.util.Base64
import com.wode.app.data.MusicTrack
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class OnlineLyricsService {
    private val client = OkHttpClient.Builder()
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    fun searchLyrics(track: MusicTrack): String? {
        val queries = buildQueries(track)
        if (queries.isEmpty()) return null

        return searchLrcLib(queries, track.durationMs)
            ?: searchOiApiQqMusic(queries)
    }

    private fun searchLrcLib(queries: List<LyricsQuery>, durationMs: Long): String? {
        return queries.firstNotNullOfOrNull { query ->
            val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
                .apply {
                    if (query.keyword.isNotBlank()) {
                        addQueryParameter("q", query.keyword)
                    } else {
                        addQueryParameter("track_name", query.trackName)
                        if (query.artistName.isNotBlank()) addQueryParameter("artist_name", query.artistName)
                    }
                }
                .build()

            runJsonArray(url.toString()) { results ->
                pickBestLrcLibLyrics(results, query, durationMs)
            }
        }
    }

    private fun searchOiApiQqMusic(queries: List<LyricsQuery>): String? {
        return queries.firstNotNullOfOrNull { query ->
            val keyword = query.keyword.ifBlank { buildKeyword(query.trackName, query.artistName) }
            if (keyword.isBlank()) return@firstNotNullOfOrNull null

            val searchUrl = "https://matomo.oiapi.net/api/QQMusicLyric".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", keyword)
                .build()

            val candidates = runJsonObject(searchUrl.toString()) { body ->
                body.extractSongCandidates()
            }.orEmpty()

            val best = candidates
                .map { it to scoreCandidate(it.title, it.artist, query) }
                .filter { it.first.mid.isNotBlank() && it.second >= MIN_FALLBACK_SCORE }
                .maxByOrNull { it.second }
                ?.first
                ?: return@firstNotNullOfOrNull null

            val lyricUrl = "https://matomo.oiapi.net/api/QQMusicLyric".toHttpUrl().newBuilder()
                .addQueryParameter("mid", best.mid)
                .build()

            runJsonObject(lyricUrl.toString()) { body ->
                body.extractLyricText()
            }
        }
    }

    private fun pickBestLrcLibLyrics(results: JSONArray, query: LyricsQuery, durationMs: Long): String? {
        var fallback: ScoredLyrics? = null
        for (index in 0 until results.length()) {
            val item = results.optJSONObject(index) ?: continue
            val syncedLyrics = item.cleanString("syncedLyrics")
            val plainLyrics = item.cleanString("plainLyrics")
            val lyrics = syncedLyrics ?: plainLyrics ?: continue

            val titleMatches = item.optString("trackName").sameTextAs(query.trackName)
            val artistMatches = query.artistName.isBlank() || item.optString("artistName").sameTextAs(query.artistName)
            val durationMatches = durationMs <= 0L || abs(item.optLong("duration") - durationMs / 1000) <= 6
            if (titleMatches && artistMatches && durationMatches) return lyrics

            val score = scoreLrcLibResult(item, query, durationMs, syncedLyrics != null)
            if (fallback == null || score > fallback.score) {
                fallback = ScoredLyrics(score = score, lyrics = lyrics)
            }
        }
        return fallback?.takeIf { it.score >= MIN_FALLBACK_SCORE }?.lyrics
    }

    private fun scoreLrcLibResult(
        item: JSONObject,
        query: LyricsQuery,
        durationMs: Long,
        hasSyncedLyrics: Boolean,
    ): Int {
        var score = scoreCandidate(item.optString("trackName"), item.optString("artistName"), query)
        if (durationMs > 0L && abs(item.optLong("duration") - durationMs / 1000) <= 8) score += 3
        if (hasSyncedLyrics) score += 1
        return score
    }

    private fun scoreCandidate(title: String, artist: String, query: LyricsQuery): Int {
        val resultTitle = cleanComparable(title)
        val resultArtist = cleanComparable(artist)
        val queryTitle = cleanComparable(query.trackName)
        val queryArtist = cleanComparable(query.artistName)
        val keywordTokens = query.keyword
            .splitToSequence(" ", "-", "\u2013", "\u2014")
            .map { cleanComparable(it) }
            .filter { it.length >= 2 }
            .toList()

        var score = 0
        if (queryTitle.isNotBlank() && resultTitle == queryTitle) score += 12
        if (queryTitle.isNotBlank() && (resultTitle.contains(queryTitle) || queryTitle.contains(resultTitle))) score += 7
        if (queryArtist.isNotBlank() && resultArtist == queryArtist) score += 8
        if (queryArtist.isNotBlank() && (resultArtist.contains(queryArtist) || queryArtist.contains(resultArtist))) score += 4
        score += keywordTokens.count { token -> resultTitle.contains(token) || resultArtist.contains(token) } * 3
        return score
    }

    private fun runJsonArray(url: String, block: (JSONArray) -> String?): String? {
        return runCatching {
            client.newCall(buildRequest(url)).execute().use { response ->
                if (!response.isSuccessful) return@use null
                block(JSONArray(response.body?.string().orEmpty()))
            }
        }.getOrNull()
    }

    private fun <T> runJsonObject(url: String, block: (JSONObject) -> T?): T? {
        return runCatching {
            client.newCall(buildRequest(url)).execute().use { response ->
                if (!response.isSuccessful) return@use null
                block(JSONObject(response.body?.string().orEmpty()))
            }
        }.getOrNull()
    }

    private fun buildRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .header("User-Agent", "WodeApp/1.0")
            .build()
    }

    private fun JSONObject.extractSongCandidates(): List<SongCandidate> {
        val arrays = listOfNotNull(
            optJSONArray("data"),
            optJSONObject("data")?.optJSONArray("list"),
            optJSONObject("data")?.optJSONArray("song"),
            optJSONObject("data")?.optJSONArray("songs"),
            optJSONObject("data")?.optJSONObject("song")?.optJSONArray("list"),
            optJSONObject("data")?.optJSONObject("song")?.optJSONArray("songlist"),
        )
        return arrays.flatMap { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        SongCandidate(
                            mid = item.firstCleanString("mid", "songmid", "songMid", "id"),
                            title = item.firstCleanString("name", "songname", "songName", "title"),
                            artist = item.extractArtistName(),
                        ),
                    )
                }
            }
        }
    }

    private fun JSONObject.extractArtistName(): String {
        val direct = firstCleanString("singer", "artist", "artistName", "author")
        if (direct.isNotBlank()) return direct
        val singerArray = optJSONArray("singer") ?: optJSONArray("artists")
        if (singerArray != null) {
            return buildList {
                for (index in 0 until singerArray.length()) {
                    val item = singerArray.optJSONObject(index) ?: continue
                    item.firstCleanString("name", "title").takeIf { it.isNotBlank() }?.let(::add)
                }
            }.joinToString(" ")
        }
        return ""
    }

    private fun JSONObject.extractLyricText(): String? {
        val data = optJSONObject("data")
        val raw = firstCleanString("lyric", "lrc", "content", "conteng", "base64", "data")
            .ifBlank {
                data?.firstCleanString("lyric", "lrc", "content", "conteng", "base64").orEmpty()
            }
        if (raw.isBlank()) return null
        return decodeIfBase64(raw)
            .replace("\\n", "\n")
            .replace("\\r", "")
            .trim()
            .takeIf { it.isNotBlank() }
    }

    private fun decodeIfBase64(value: String): String {
        val compact = value.trim()
        if (!Regex("^[A-Za-z0-9+/=\\r\\n]+$").matches(compact) || compact.length < 16) return value
        return runCatching {
            String(Base64.decode(compact, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrDefault(value)
    }

    private fun buildQueries(track: MusicTrack): List<LyricsQuery> {
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

        val candidates = mutableListOf(
            LyricsQuery(keyword = buildKeyword(rawTitle, artist)),
            LyricsQuery(keyword = buildKeyword(title, artist)),
            LyricsQuery(trackName = title, artistName = artist),
            LyricsQuery(trackName = rawTitle, artistName = artist),
        )

        if (parts.size >= 2) {
            val left = parts.first()
            val right = parts.drop(1).joinToString("-")
            candidates += LyricsQuery(keyword = left)
            candidates += LyricsQuery(keyword = right)
            candidates += LyricsQuery(keyword = buildKeyword(right, left))
            candidates += LyricsQuery(keyword = buildKeyword(left, right))
            candidates += LyricsQuery(trackName = right, artistName = artist.ifBlank { left })
            candidates += LyricsQuery(trackName = left, artistName = artist.ifBlank { right })
            splitAttachedArtist(left)?.let { attached ->
                candidates += LyricsQuery(keyword = buildKeyword(attached.title, attached.artist))
                candidates += LyricsQuery(trackName = attached.title, artistName = attached.artist)
            }
            splitAttachedArtist(right)?.let { attached ->
                candidates += LyricsQuery(keyword = buildKeyword(attached.title, attached.artist))
                candidates += LyricsQuery(trackName = attached.title, artistName = attached.artist)
            }
        }

        return candidates
            .map {
                it.copy(
                    trackName = cleanSongName(it.trackName),
                    artistName = cleanArtistName(it.artistName),
                    keyword = cleanKeyword(it.keyword),
                )
            }
            .filter { it.trackName.isNotBlank() || it.keyword.isNotBlank() }
            .distinct()
    }

    private fun cleanText(value: String): String {
        return value
            .replace(Regex("\\.(mp3|m4a|aac|flac|ogg|opus|wav)$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\u603b\\s*Music", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[_\\s-]+\\d{1,2}[-_:]\\d{2}[-_:]\\d{2}$"), "")
            .replace(Regex("\\.{2,}|\u2026+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun cleanSongName(value: String): String {
        return cleanText(value)
            .substringBefore("(")
            .substringBefore("\uFF08")
            .trim()
    }

    private fun cleanArtistName(value: String): String {
        return cleanText(value)
            .replace(Regex("[&\u3001\uFF0C,/]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun buildKeyword(title: String, artist: String): String {
        return listOf(cleanSongName(title), cleanArtistName(artist))
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    private fun splitAttachedArtist(value: String): AttachedArtist? {
        val cleanValue = cleanText(value)
        if (cleanValue.length < 4) return null
        val artist = knownArtists
            .filter { cleanValue.endsWith(it) && cleanValue.length > it.length }
            .maxByOrNull { it.length }
            ?: return null
        val title = cleanValue.removeSuffix(artist).trim()
        if (title.length < 2) return null
        return AttachedArtist(title = title, artist = artist)
    }

    private fun cleanKeyword(value: String): String {
        return cleanText(value)
            .replace(Regex("[&\u3001\uFF0C,/]+"), " ")
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

    private fun JSONObject.cleanString(name: String): String? {
        if (isNull(name)) return null
        return optString(name).trim().takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
    }

    private fun JSONObject.firstCleanString(vararg names: String): String {
        return names.firstNotNullOfOrNull { cleanString(it) }.orEmpty()
    }

    private data class LyricsQuery(
        val trackName: String = "",
        val artistName: String = "",
        val keyword: String = "",
    )

    private data class SongCandidate(
        val mid: String,
        val title: String,
        val artist: String,
    )

    private data class AttachedArtist(
        val title: String,
        val artist: String,
    )

    private data class ScoredLyrics(
        val score: Int,
        val lyrics: String,
    )

    private companion object {
        private const val MIN_FALLBACK_SCORE = 7
        private val knownArtists = listOf(
            "\u5468\u6770\u4f26",
            "\u5f20\u97f6\u6db5",
            "\u90ed\u6e90\u6f6e",
            "\u5b8b\u51ac\u91ce",
            "\u53f6\u60e0\u7f8e",
            "\u6734\u6811",
            "\u6a59\u6708",
            "G.E.M.",
            "GEM",
        )
    }
}
