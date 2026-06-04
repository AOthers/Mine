package com.wode.app.service

import com.wode.app.data.ReaderItemType

object ReaderFormat {
    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif")
    private val textExtensions = setOf("txt")
    private val epubExtensions = setOf("epub")
    private val pdfExtensions = setOf("pdf")
    private val comicArchiveExtensions = setOf("zip", "cbz")

    val naturalNameComparator: Comparator<String> = Comparator { left, right ->
        compareNatural(left, right)
    }

    fun classifyFileName(name: String): ReaderItemType? {
        val extension = extensionOf(name)
        return when {
            extension in textExtensions -> ReaderItemType.TEXT
            extension in epubExtensions -> ReaderItemType.EPUB
            extension in pdfExtensions -> ReaderItemType.PDF
            extension in comicArchiveExtensions -> ReaderItemType.COMIC_ARCHIVE
            else -> null
        }
    }

    fun isSupportedBookOrArchive(name: String): Boolean = classifyFileName(name) != null

    fun isSupportedImage(name: String): Boolean = extensionOf(name) in imageExtensions

    fun isSafeZipEntry(name: String): Boolean {
        val normalized = name.replace('\\', '/')
        if (normalized.startsWith("/")) return false
        return normalized.split('/').none { it == ".." }
    }

    fun extensionOf(name: String): String {
        return name.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
    }

    private fun compareNatural(left: String, right: String): Int {
        var leftIndex = 0
        var rightIndex = 0
        while (leftIndex < left.length && rightIndex < right.length) {
            val leftChar = left[leftIndex]
            val rightChar = right[rightIndex]
            if (leftChar.isDigit() && rightChar.isDigit()) {
                val leftStart = leftIndex
                val rightStart = rightIndex
                while (leftIndex < left.length && left[leftIndex].isDigit()) leftIndex++
                while (rightIndex < right.length && right[rightIndex].isDigit()) rightIndex++
                val leftNumber = left.substring(leftStart, leftIndex).trimStart('0').ifBlank { "0" }
                val rightNumber = right.substring(rightStart, rightIndex).trimStart('0').ifBlank { "0" }
                if (leftNumber.length != rightNumber.length) {
                    return leftNumber.length.compareTo(rightNumber.length)
                }
                val numberCompare = leftNumber.compareTo(rightNumber)
                if (numberCompare != 0) return numberCompare
            } else {
                val charCompare = leftChar.lowercaseChar().compareTo(rightChar.lowercaseChar())
                if (charCompare != 0) return charCompare
                leftIndex++
                rightIndex++
            }
        }
        return (left.length - leftIndex).compareTo(right.length - rightIndex)
    }
}
