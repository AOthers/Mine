package com.wode.app.service

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.wode.app.data.ReaderItem
import com.wode.app.data.ReaderItemType
import java.util.UUID

class ReaderImportService(private val context: Context) {

    fun importFile(uri: Uri): Result<List<ReaderItem>> {
        val file = DocumentFile.fromSingleUri(context, uri)
            ?: return Result.failure(IllegalArgumentException("无法打开已选择的文件"))
        val name = runCatching { file.name }.getOrNull()
            ?: uri.lastPathSegment
            ?: "未命名"
        val type = ReaderFormat.classifyFileName(name)
            ?: return Result.failure(IllegalArgumentException("暂不支持这个文件格式"))
        return Result.success(listOf(buildItem(uri, name, type, name)))
    }

    fun importFolder(uri: Uri): Result<List<ReaderItem>> {
        val folder = DocumentFile.fromTreeUri(context, uri)
            ?: return Result.failure(IllegalArgumentException("无法打开已选择的文件夹"))
        val children = runCatching { folder.listFiles().toList() }
            .getOrElse { return Result.failure(it) }
        val imported = mutableListOf<ReaderItem>()

        val images = children
            .filter { it.isFile && ReaderFormat.isSupportedImage(it.name.orEmpty()) }
            .sortedWith { left, right ->
                ReaderFormat.naturalNameComparator.compare(left.name.orEmpty(), right.name.orEmpty())
            }
        if (images.isNotEmpty()) {
            val title = runCatching { folder.name }.getOrNull() ?: "漫画文件夹"
            imported += buildItem(
                uri = uri,
                title = title,
                type = ReaderItemType.COMIC_FOLDER,
                displayPath = title,
                pageCount = images.size,
            )
        }

        children
            .filter { it.isFile && ReaderFormat.isSupportedBookOrArchive(it.name.orEmpty()) }
            .forEach { file ->
                val name = runCatching { file.name }.getOrNull() ?: return@forEach
                val type = ReaderFormat.classifyFileName(name) ?: return@forEach
                imported += buildItem(
                    uri = file.uri,
                    title = name.substringBeforeLast('.').ifBlank { name },
                    type = type,
                    displayPath = name,
                    parentUri = uri.toString(),
                )
            }

        return if (imported.isEmpty()) {
            Result.failure(IllegalArgumentException("没有找到支持的小说或漫画文件"))
        } else {
            Result.success(imported.distinctBy { it.sourceUri })
        }
    }

    private fun buildItem(
        uri: Uri,
        title: String,
        type: ReaderItemType,
        displayPath: String,
        parentUri: String? = null,
        pageCount: Int = 0,
    ): ReaderItem {
        return ReaderItem(
            id = UUID.randomUUID().toString(),
            title = title.substringBeforeLast('.').ifBlank { title },
            type = type,
            sourceUri = uri.toString(),
            parentUri = parentUri,
            displayPath = displayPath,
            pageCount = pageCount,
        )
    }
}
