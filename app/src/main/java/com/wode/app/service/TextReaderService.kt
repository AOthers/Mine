package com.wode.app.service

import android.content.Context
import android.net.Uri
import com.wode.app.data.TextContent
import java.nio.charset.Charset

class TextReaderService(private val context: Context) {

    fun load(uri: Uri, title: String): Result<TextContent> = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("无法读取文本文件")
        val text = decodeText(bytes)
        TextContent(title = title, text = text)
    }

    private fun decodeText(bytes: ByteArray): String {
        val utf8 = runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull()
        if (!utf8.isNullOrBlank() && utf8.count { it == '\uFFFD' } < 8) return utf8
        return bytes.toString(Charset.forName("GBK"))
    }
}
