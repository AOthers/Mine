package com.wode.app.data

enum class ReaderItemType {
    TEXT,
    EPUB,
    PDF,
    COMIC_FOLDER,
    COMIC_ARCHIVE,
}

data class ReaderItem(
    val id: String,
    val title: String,
    val type: ReaderItemType,
    val sourceUri: String,
    val parentUri: String? = null,
    val displayPath: String = "",
    val progress: Float = 0f,
    val progressIndex: Int = 0,
    val progressLabel: String = "",
    val lastOpenedAt: Long = 0L,
    val pageCount: Int = 0,
)

data class ReaderSettings(
    val fontSizeSp: Int = 18,
    val theme: ReaderTheme = ReaderTheme.LIGHT,
    val pageMode: ReaderPageMode = ReaderPageMode.SCROLL,
    val firstLineIndent: Boolean = true,
)

enum class ReaderTheme {
    LIGHT,
    SEPIA,
    DARK,
}

enum class ReaderPageMode {
    SCROLL,
    PAGE,
}

data class ReaderChapter(
    val title: String,
    val targetIndex: Int,
)

data class TextContent(
    val title: String,
    val text: String,
    val blocks: List<ReaderContentBlock> = if (text.isBlank()) {
        emptyList()
    } else {
        listOf(ReaderContentBlock.Text(text))
    },
    val chapters: List<ReaderChapter> = emptyList(),
)

sealed class ReaderContentBlock {
    data class Text(val text: String) : ReaderContentBlock()
    data class Image(val name: String, val uri: String) : ReaderContentBlock()
}

data class ComicImage(
    val name: String,
    val uri: String? = null,
    val archiveEntry: String? = null,
)
