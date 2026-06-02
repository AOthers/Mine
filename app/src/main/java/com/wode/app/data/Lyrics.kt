package com.wode.app.data

data class LyricLine(
    val timeMs: Long,
    val text: String,
)

data class Lyrics(
    val lines: List<LyricLine> = emptyList(),
    val plainText: String? = null,
) {
    fun currentLine(positionMs: Long): LyricLine? {
        return lines.lastOrNull { it.timeMs <= positionMs && it.text.isDisplayableLyric() }
    }

    fun nextLine(positionMs: Long): LyricLine? {
        return lines.firstOrNull { it.timeMs > positionMs && it.text.isDisplayableLyric() }
    }

    companion object {
        val Empty = Lyrics()
    }
}

private fun String.isDisplayableLyric(): Boolean {
    return isNotBlank() && !equals("null", ignoreCase = true)
}
