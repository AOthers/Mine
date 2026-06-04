package com.wode.app.service

import android.content.Context
import android.net.Uri
import com.wode.app.data.ReaderChapter
import com.wode.app.data.ReaderContentBlock
import com.wode.app.data.TextContent
import java.io.File
import java.net.URLDecoder
import java.util.UUID
import java.util.zip.ZipException
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

class EpubReaderService(private val context: Context) {

    fun load(uri: Uri, title: String): Result<TextContent> = runCatching {
        val epubFile = copyToCache(uri)
        val imageDir = File(context.cacheDir, "reader_epub_images/${UUID.randomUUID()}").also { it.mkdirs() }
        val parsed = ZipFile(epubFile).use { zip ->
            val metadata = readMetadata(zip)
            val contentPaths = metadata.spinePaths
                .filter { isReadableContentPath(it) }
                .ifEmpty { findHtmlPaths(zip).filter { isReadableContentPath(it) } }
            val pathToBlockIndex = linkedMapOf<String, Int>()
            val blocks = mutableListOf<ReaderContentBlock>()
            metadata.coverImagePath?.let { cover ->
                extractImageByPath(zip, cover, imageDir)?.let { uri ->
                    blocks += ReaderContentBlock.Image(name = "封面", uri = uri)
                }
            }
            contentPaths.forEach { path ->
                pathToBlockIndex[path] = blocks.size
                blocks += readHtmlBlocks(zip, path, imageDir)
            }
            val chapters = buildEpubChapters(metadata.navItems, pathToBlockIndex, contentPaths)
            ParsedEpub(blocks = blocks, chapters = chapters)
        }
        val text = parsed.blocks.filterIsInstance<ReaderContentBlock.Text>()
            .joinToString("\n\n") { it.text }
            .trim()
        if (parsed.blocks.isEmpty() || text.isBlank() && parsed.blocks.none { it is ReaderContentBlock.Image }) {
            throw IllegalArgumentException("EPUB 中没有找到可阅读正文")
        }
        TextContent(title = title, text = text, blocks = parsed.blocks, chapters = parsed.chapters)
    }

    private fun copyToCache(uri: Uri): File {
        val dir = File(context.cacheDir, "reader_epubs").also { it.mkdirs() }
        val file = File(dir, "${uri.toString().hashCode()}.epub")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalArgumentException("无法读取 EPUB 文件")
        return file
    }

    private fun readMetadata(zip: ZipFile): EpubMetadata {
        val opfPath = findOpfPath(zip)
        val opfEntry = opfPath?.let { zip.getEntry(it) }
        if (opfPath == null || opfEntry == null) return EpubMetadata()
        val document = zip.getInputStream(opfEntry).use { parseXml(it.readBytes()) }
        val items = document.getElementsByTagName("item").asElements()
        val manifest = items.mapNotNull { item ->
            val id = item.getAttribute("id")
            val href = item.getAttribute("href")
            if (id.isBlank() || href.isBlank()) {
                null
            } else {
                id to ManifestItem(
                    href = resolveEntryPath(opfPath, href).substringBefore('#'),
                    mediaType = item.getAttribute("media-type"),
                    properties = item.getAttribute("properties"),
                )
            }
        }.toMap()
        val coverId = document.getElementsByTagName("meta")
            .asElements()
            .firstOrNull { it.getAttribute("name").equals("cover", ignoreCase = true) }
            ?.getAttribute("content")
        val coverImagePath = coverId
            ?.let { manifest[it]?.href }
            ?: manifest.values.firstOrNull {
                it.properties.contains("cover-image", ignoreCase = true) ||
                    it.href.substringAfterLast('/').contains("cover", ignoreCase = true)
            }?.href
        val spinePaths = document.getElementsByTagName("itemref")
            .asElements()
            .mapNotNull { manifest[it.getAttribute("idref")]?.href }
            .filter { isHtmlEntry(it) && ReaderFormat.isSafeZipEntry(it) }
        val navPath = manifest.values.firstOrNull { it.properties.contains("nav", ignoreCase = true) }?.href
        val ncxPath = manifest.values.firstOrNull { it.mediaType.equals("application/x-dtbncx+xml", ignoreCase = true) }?.href
        val navItems = readNavItems(zip, navPath) + readNcxItems(zip, ncxPath)
        return EpubMetadata(spinePaths = spinePaths, navItems = navItems, coverImagePath = coverImagePath)
    }

    private fun findOpfPath(zip: ZipFile): String? {
        val container = zip.getEntry("META-INF/container.xml")
        if (container != null) {
            runCatching {
                val document = zip.getInputStream(container).use { parseXml(it.readBytes()) }
                document.getElementsByTagName("rootfile")
                    .asElements()
                    .firstNotNullOfOrNull { it.getAttribute("full-path").takeIf(String::isNotBlank) }
            }.getOrNull()?.let { return it }
        }
        return zip.entries().asSequence()
            .map { it.name }
            .firstOrNull { it.endsWith(".opf", ignoreCase = true) && ReaderFormat.isSafeZipEntry(it) }
    }

    private fun findHtmlPaths(zip: ZipFile): List<String> {
        return zip.entries().asSequence()
            .map { it.name }
            .filter { isHtmlEntry(it) && ReaderFormat.isSafeZipEntry(it) }
            .sortedWith(ReaderFormat.naturalNameComparator)
            .toList()
    }

    private fun readNavItems(zip: ZipFile, navPath: String?): List<NavItem> {
        if (navPath == null) return emptyList()
        return runCatching {
            val entry = zip.getEntry(navPath) ?: return emptyList()
            val raw = zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
            Regex("<a\\b[^>]*href\\s*=\\s*(['\"])(.*?)\\1[^>]*>([\\s\\S]*?)</a>", RegexOption.IGNORE_CASE)
                .findAll(raw)
                .mapNotNull { match ->
                    val href = resolveEntryPath(navPath, match.groupValues[2])
                    val label = htmlToPlainText(match.groupValues[3]).ifBlank { return@mapNotNull null }
                    NavItem(label, href)
                }
                .toList()
        }.getOrDefault(emptyList())
    }

    private fun readNcxItems(zip: ZipFile, ncxPath: String?): List<NavItem> {
        if (ncxPath == null) return emptyList()
        return runCatching {
            val entry = zip.getEntry(ncxPath) ?: return emptyList()
            val document = zip.getInputStream(entry).use { parseXml(it.readBytes()) }
            document.getElementsByTagName("navPoint").asElements().mapNotNull { navPoint ->
                val label = navPoint.getElementsByTagName("text")
                    .asElements()
                    .firstOrNull()
                    ?.textContent
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val src = navPoint.getElementsByTagName("content")
                    .asElements()
                    .firstOrNull()
                    ?.getAttribute("src")
                    ?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                NavItem(label, resolveEntryPath(ncxPath, src))
            }
        }.getOrDefault(emptyList())
    }

    private fun buildEpubChapters(
        navItems: List<NavItem>,
        pathToBlockIndex: Map<String, Int>,
        contentPaths: List<String>,
    ): List<ReaderChapter> {
        val fromNav = navItems.mapNotNull { item ->
            val path = item.href.substringBefore('#')
            val target = pathToBlockIndex[path] ?: return@mapNotNull null
            ReaderChapter(item.title.take(60), target)
        }.distinctBy { it.targetIndex }
        return fromNav.ifEmpty {
            contentPaths.mapIndexed { index, path ->
                ReaderChapter(path.substringAfterLast('/').substringBeforeLast('.').ifBlank { "第 ${index + 1} 章" }, pathToBlockIndex[path] ?: index)
            }
        }
    }

    private fun readHtmlBlocks(zip: ZipFile, htmlPath: String, imageDir: File): List<ReaderContentBlock> {
        return try {
            val entry = zip.getEntry(htmlPath) ?: return emptyList()
            val raw = zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
            htmlToBlocks(raw, htmlPath, zip, imageDir)
        } catch (_: ZipException) {
            emptyList()
        }
    }

    private fun htmlToBlocks(
        raw: String,
        htmlPath: String,
        zip: ZipFile,
        imageDir: File,
    ): List<ReaderContentBlock> {
        val withoutScripts = raw.replace(Regex("<(script|style)[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE), "")
        val imageRegex = Regex("<img\\b[^>]*(?:src|data-src)\\s*=\\s*(['\"])(.*?)\\1[^>]*>", RegexOption.IGNORE_CASE)
        val blocks = mutableListOf<ReaderContentBlock>()
        var cursor = 0
        imageRegex.findAll(withoutScripts).forEach { match ->
            val before = htmlToPlainText(withoutScripts.substring(cursor, match.range.first))
            if (before.isNotBlank()) blocks += ReaderContentBlock.Text(before)
            val src = match.groupValues[2]
            extractImage(zip, htmlPath, src, imageDir)?.let { imageUri ->
                blocks += ReaderContentBlock.Image(name = src.substringAfterLast('/'), uri = imageUri)
            }
            cursor = match.range.last + 1
        }
        val tail = htmlToPlainText(withoutScripts.substring(cursor))
        if (tail.isNotBlank()) blocks += ReaderContentBlock.Text(tail)
        return blocks
    }

    private fun extractImage(zip: ZipFile, htmlPath: String, src: String, imageDir: File): String? {
        val imagePath = resolveEntryPath(htmlPath, src).substringBefore('#')
        return extractImageByPath(zip, imagePath, imageDir)
    }

    private fun extractImageByPath(zip: ZipFile, imagePath: String, imageDir: File): String? {
        if (!ReaderFormat.isSafeZipEntry(imagePath) || !ReaderFormat.isSupportedImage(imagePath)) return null
        return try {
            val entry = zip.getEntry(imagePath) ?: return null
            val suffix = imagePath.substringAfterLast('.', "img")
            val imageFile = File(imageDir, "${imagePath.hashCode()}.$suffix")
            zip.getInputStream(entry).use { input ->
                imageFile.outputStream().use { output -> input.copyTo(output) }
            }
            Uri.fromFile(imageFile).toString()
        } catch (_: ZipException) {
            null
        }
    }

    private fun resolveEntryPath(currentPath: String, href: String): String {
        val decoded = URLDecoder.decode(href.substringBefore('#'), "UTF-8")
        val base = currentPath.substringBeforeLast('/', missingDelimiterValue = "")
        val joined = if (base.isBlank()) decoded else "$base/$decoded"
        val parts = ArrayDeque<String>()
        joined.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeLast()
                else -> parts.addLast(part)
            }
        }
        val anchor = href.substringAfter('#', missingDelimiterValue = "")
        return parts.joinToString("/") + if (anchor.isBlank()) "" else "#$anchor"
    }

    private fun isReadableContentPath(path: String): Boolean {
        val lower = path.lowercase()
        return isHtmlEntry(path) &&
            !lower.contains("cover") &&
            !lower.contains("bookcover") &&
            !lower.contains("titlepage") &&
            !lower.contains("nav.") &&
            !lower.contains("toc.")
    }

    private fun isHtmlEntry(name: String): Boolean {
        val clean = name.substringBefore('#')
        return clean.endsWith(".xhtml", true) ||
            clean.endsWith(".html", true) ||
            clean.endsWith(".htm", true)
    }

    private fun parseXml(bytes: ByteArray) =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            setFeatureSafely("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeatureSafely("http://xml.org/sax/features/external-general-entities", false)
            setFeatureSafely("http://xml.org/sax/features/external-parameter-entities", false)
        }.newDocumentBuilder().parse(bytes.inputStream())

    private fun DocumentBuilderFactory.setFeatureSafely(name: String, value: Boolean) {
        runCatching { setFeature(name, value) }
    }

    private fun org.w3c.dom.NodeList.asElements(): List<Element> {
        return (0 until length).mapNotNull { item(it) as? Element }
    }

    private fun htmlToPlainText(raw: String): String {
        return raw
            .replace(Regex("</(p|div|h[1-6]|li|br)>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private data class ManifestItem(
        val href: String,
        val mediaType: String,
        val properties: String,
    )

    private data class EpubMetadata(
        val spinePaths: List<String> = emptyList(),
        val navItems: List<NavItem> = emptyList(),
        val coverImagePath: String? = null,
    )

    private data class NavItem(val title: String, val href: String)

    private data class ParsedEpub(
        val blocks: List<ReaderContentBlock>,
        val chapters: List<ReaderChapter>,
    )
}
