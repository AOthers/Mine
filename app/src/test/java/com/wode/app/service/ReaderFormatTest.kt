package com.wode.app.service

import com.wode.app.data.ReaderItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderFormatTest {

    @Test
    fun classifySupportedBookAndComicFormats() {
        assertEquals(ReaderItemType.TEXT, ReaderFormat.classifyFileName("story.txt"))
        assertEquals(ReaderItemType.EPUB, ReaderFormat.classifyFileName("novel.epub"))
        assertEquals(ReaderItemType.PDF, ReaderFormat.classifyFileName("manual.pdf"))
        assertEquals(ReaderItemType.COMIC_ARCHIVE, ReaderFormat.classifyFileName("comic.cbz"))
        assertEquals(ReaderItemType.COMIC_ARCHIVE, ReaderFormat.classifyFileName("comic.zip"))
    }

    @Test
    fun naturalSortOrdersNumberedImages() {
        val sorted = listOf("10.jpg", "2.jpg", "1.jpg").sortedWith(ReaderFormat.naturalNameComparator)

        assertEquals(listOf("1.jpg", "2.jpg", "10.jpg"), sorted)
    }

    @Test
    fun rejectsUnsafeZipEntries() {
        assertFalse(ReaderFormat.isSafeZipEntry("../secret.jpg"))
        assertFalse(ReaderFormat.isSafeZipEntry("/root/secret.jpg"))
        assertTrue(ReaderFormat.isSafeZipEntry("chapter/001.jpg"))
    }
}
