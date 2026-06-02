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
        return lines.lastOrNull { it.timeMs <= positionMs }
    }

    fun nextLine(positionMs: Long): LyricLine? {
        return lines.firstOrNull { it.timeMs > positionMs }
    }

    companion object {
        val Empty = Lyrics()
    }
}
