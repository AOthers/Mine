package com.wode.app.data

import android.net.Uri

data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val modifiedTimeMs: Long,
    val uri: Uri,
    val source: MusicSource,
    val folderName: String? = null,
    val folderRootUri: String? = null,
    val lyricUri: Uri? = null,
) {
    val displayArtist: String
        get() = artist.ifBlank { "\u672a\u77e5\u6b4c\u624b" }
}

enum class MusicSource {
    SYSTEM,
    FOLDER,
}
