package com.wode.app.service

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.wode.app.data.ComicImage
import java.io.File
import java.util.zip.ZipInputStream

class ComicReaderService(private val context: Context) {

    fun listFolderImages(uri: Uri): Result<List<ComicImage>> = runCatching {
        val folder = DocumentFile.fromTreeUri(context, uri)
            ?: throw IllegalArgumentException("无法打开漫画文件夹")
        folder.listFiles()
            .filter { it.isFile && ReaderFormat.isSupportedImage(it.name.orEmpty()) }
            .sortedWith { left, right ->
                ReaderFormat.naturalNameComparator.compare(left.name.orEmpty(), right.name.orEmpty())
            }
            .map { file ->
                ComicImage(
                    name = file.name ?: "image",
                    uri = file.uri.toString(),
                )
            }
            .ifEmpty { throw IllegalArgumentException("这个文件夹里没有支持的漫画图片") }
    }

    fun extractArchiveImages(uri: Uri, itemId: String): Result<List<ComicImage>> = runCatching {
        val dir = File(context.cacheDir, "reader_comics/$itemId").also { it.mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val images = mutableListOf<ComicImage>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (!entry.isDirectory && ReaderFormat.isSafeZipEntry(name) && ReaderFormat.isSupportedImage(name)) {
                        val safeName = name.substringAfterLast('/').ifBlank { "image-${images.size}" }
                        val file = File(dir, "${images.size.toString().padStart(5, '0')}-$safeName")
                        file.outputStream().use { output -> zip.copyTo(output) }
                        images += ComicImage(
                            name = name,
                            uri = Uri.fromFile(file).toString(),
                            archiveEntry = name,
                        )
                    }
                    zip.closeEntry()
                }
            }
        } ?: throw IllegalArgumentException("无法读取漫画压缩包")
        images.sortedWith { left, right ->
            ReaderFormat.naturalNameComparator.compare(left.name, right.name)
        }.ifEmpty { throw IllegalArgumentException("压缩包里没有支持的漫画图片") }
    }
}
