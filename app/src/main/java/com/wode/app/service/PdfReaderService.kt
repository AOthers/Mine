package com.wode.app.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

class PdfReaderService(private val context: Context) {

    fun getPageCount(uri: Uri): Result<Int> = runCatching {
        openRenderer(uri).use { renderer -> renderer.pageCount }
    }

    fun renderPage(uri: Uri, pageIndex: Int, targetWidth: Int = 1200): Result<Bitmap> = runCatching {
        openRenderer(uri).use { renderer ->
            val safeIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
            renderer.openPage(safeIndex).use { page ->
                val scale = targetWidth.toFloat() / page.width.toFloat()
                val width = targetWidth
                val height = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }

    fun renderPageToCache(uri: Uri, cacheKey: String, pageIndex: Int, targetWidth: Int = 1800): Result<String> = runCatching {
        val safeKey = cacheKey.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val dir = File(context.cacheDir, "reader_pdf/$safeKey").apply { mkdirs() }
        val file = File(dir, "page_${pageIndex}_w$targetWidth.jpg")
        if (file.exists() && file.length() > 0L) return@runCatching file.toURI().toString()
        val bitmap = renderPage(uri, pageIndex, targetWidth).getOrThrow()
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 98, output)
        }
        bitmap.recycle()
        file.toURI().toString()
    }

    private fun openRenderer(uri: Uri): PdfRenderer {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalArgumentException("无法打开 PDF 文件")
        return PdfRenderer(descriptor)
    }
}
