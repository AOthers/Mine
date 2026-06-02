package com.wode.app.service

import com.wode.app.data.LyricLine
import com.wode.app.data.Lyrics

object LyricParser {
    private val timestampRegex = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

    fun parseLrc(content: String): Lyrics {
        val lines = content
            .lineSequence()
            .flatMap { rawLine ->
                val matches = timestampRegex.findAll(rawLine).toList()
                if (matches.isEmpty()) {
                    emptySequence()
                } else {
                    val text = timestampRegex.replace(rawLine, "").trim()
                    matches.asSequence().mapNotNull { match ->
                        parseTimestamp(match)?.let { timeMs -> LyricLine(timeMs, text) }
                    }
                }
            }
            .sortedBy { it.timeMs }
            .toList()

        return Lyrics(lines = lines)
    }

    fun parseEmbeddedLyrics(content: String): Lyrics {
        val parsed = parseLrc(content)
        return if (parsed.lines.isNotEmpty()) parsed else Lyrics(plainText = content.trim().ifEmpty { null })
    }

    private fun parseTimestamp(match: MatchResult): Long? {
        val minutes = match.groupValues.getOrNull(1)?.toLongOrNull() ?: return null
        val seconds = match.groupValues.getOrNull(2)?.toLongOrNull() ?: return null
        val fraction = match.groupValues.getOrNull(3).orEmpty()
        val fractionMs = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLongOrNull()?.times(100)
            2 -> fraction.toLongOrNull()?.times(10)
            else -> fraction.take(3).toLongOrNull()
        } ?: return null

        return minutes * 60_000 + seconds * 1_000 + fractionMs
    }
}
