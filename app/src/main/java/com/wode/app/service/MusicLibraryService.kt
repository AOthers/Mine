package com.wode.app.service

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore.Files.FileColumns
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.wode.app.data.MusicSource
import com.wode.app.data.MusicTrack
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

class MusicLibraryService(private val context: Context) {
    fun loadLibrary(folderUris: List<Uri>, includeSystemLibrary: Boolean): List<MusicTrack> {
        val tracks = buildList {
            if (includeSystemLibrary) {
                addAll(loadSystemTracks())
            }
            folderUris.forEach { addAll(loadFolderTracks(it)) }
        }
        return tracks.distinctBy { it.uri.toString() }
    }

    fun readLyrics(track: MusicTrack): String? {
        track.lyricUri?.let { uri ->
            return runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
        }
        return readFlacLyrics(track.uri) ?: readEmbeddedLyrics(track.uri)
    }

    fun renameAudioFile(track: MusicTrack, title: String): FileRenameResult {
        return runCatching {
            if (track.source != MusicSource.FOLDER) return@runCatching FileRenameResult.UNSUPPORTED_SOURCE
            val cleanTitle = title.toSafeFileBaseName()
            if (cleanTitle.isBlank()) return@runCatching FileRenameResult.FAILED
            val document = findFolderDocument(track) ?: return@runCatching FileRenameResult.FAILED
            if (!document.canWrite()) return@runCatching FileRenameResult.NO_PERMISSION
            val newDisplayName = buildRenamedDisplayName(document.name.orEmpty(), cleanTitle)
            if (document.renameTo(newDisplayName)) FileRenameResult.SUCCESS else FileRenameResult.FAILED
        }.getOrDefault(FileRenameResult.FAILED)
    }

    fun deleteAudioFile(track: MusicTrack): FileDeleteResult {
        return runCatching {
            if (track.source != MusicSource.FOLDER) return@runCatching FileDeleteResult.UNSUPPORTED_SOURCE
            val document = findFolderDocument(track) ?: return@runCatching FileDeleteResult.FAILED
            if (!document.canWrite()) return@runCatching FileDeleteResult.NO_PERMISSION
            if (document.delete()) FileDeleteResult.SUCCESS else FileDeleteResult.FAILED
        }.getOrDefault(FileDeleteResult.FAILED)
    }

    private fun loadSystemTracks(): List<MusicTrack> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val lyricsByPath = loadSystemLyricIndex()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.RELATIVE_PATH,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC}=1"
        return runCatching {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val displayNameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val relativePathCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
                buildList {
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val displayName = cursor.getString(displayNameCol).orEmpty()
                        val baseName = displayName.substringBeforeLast(".")
                        val relativePath = cursor.getString(relativePathCol).orEmpty()
                        val nameParts = normalizeSystemTrackName(
                            title = cursor.getString(titleCol).orEmpty(),
                            artist = cursor.getString(artistCol).orEmpty(),
                            baseName = baseName,
                        )
                        add(
                            MusicTrack(
                                id = "system:$id",
                                title = nameParts.title.ifBlank { baseName.ifBlank { "\u672a\u77e5\u6b4c\u66f2" } },
                                artist = nameParts.artist,
                                album = cursor.getString(albumCol).orEmpty(),
                                durationMs = cursor.getLong(durationCol),
                                modifiedTimeMs = cursor.getLong(modifiedCol) * 1000L,
                                uri = ContentUris.withAppendedId(collection, id),
                                source = MusicSource.SYSTEM,
                                lyricUri = lyricsByPath[lyricKey(relativePath, baseName)],
                            ),
                        )
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun loadSystemLyricIndex(): Map<String, Uri> {
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            FileColumns._ID,
            FileColumns.DISPLAY_NAME,
            FileColumns.RELATIVE_PATH,
        )
        val selection = "${FileColumns.DISPLAY_NAME} LIKE ?"
        return runCatching {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                arrayOf("%.lrc"),
                null,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(FileColumns._ID)
                val displayNameCol = cursor.getColumnIndexOrThrow(FileColumns.DISPLAY_NAME)
                val relativePathCol = cursor.getColumnIndexOrThrow(FileColumns.RELATIVE_PATH)
                buildMap {
                    while (cursor.moveToNext()) {
                        val displayName = cursor.getString(displayNameCol).orEmpty()
                        val baseName = displayName.substringBeforeLast(".")
                        val relativePath = cursor.getString(relativePathCol).orEmpty()
                        put(lyricKey(relativePath, baseName), ContentUris.withAppendedId(collection, cursor.getLong(idCol)))
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyMap())
    }

    private fun normalizeSystemTrackName(title: String, artist: String, baseName: String): TrackNameParts {
        val cleanTitle = cleanTrackText(title).ifBlank { cleanTrackText(baseName) }
        val cleanArtist = cleanTrackText(artist).takeUnless { it.isUnknownArtist() }.orEmpty()
        if (cleanTitle.isKnownArtistName() && cleanArtist.isNotBlank() && !cleanArtist.isKnownArtistName()) {
            return TrackNameParts(title = cleanArtist, artist = cleanTitle).withCleanArtistTitleOverlap()
        }
        val parsedFromName = parseTrackName(baseName)
        if (cleanArtist.isBlank() && parsedFromName.artist.isNotBlank()) {
            return parsedFromName
        }
        return TrackNameParts(title = cleanTitle, artist = cleanArtist).withCleanArtistTitleOverlap()
    }

    private fun loadFolderTracks(treeUri: Uri): List<MusicTrack> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        return scanDirectory(root, root.name ?: "\u81ea\u9009\u6587\u4ef6\u5939", treeUri.toString())
    }

    private fun scanDirectory(directory: DocumentFile, folderName: String, folderRootUri: String): List<MusicTrack> {
        val children = directory.listFiles()
        val lyricsByBaseName = children
            .filter { it.isFile && it.name?.endsWith(".lrc", ignoreCase = true) == true }
            .associateBy { it.name.orEmpty().substringBeforeLast(".").lowercase(Locale.getDefault()) }

        return buildList {
            children.forEach { file ->
                when {
                    file.isDirectory -> addAll(scanDirectory(file, folderName, folderRootUri))
                    file.isFile && isSupportedAudio(file.name.orEmpty()) -> {
                        val baseName = file.name.orEmpty().substringBeforeLast(".")
                        val nameParts = parseTrackName(baseName)
                        add(
                            MusicTrack(
                                id = "folder:${file.uri}",
                                title = nameParts.title.ifBlank { "\u672a\u77e5\u6b4c\u66f2" },
                                artist = nameParts.artist,
                                album = "",
                                durationMs = 0L,
                                modifiedTimeMs = file.lastModified(),
                                uri = file.uri,
                                source = MusicSource.FOLDER,
                                folderName = folderName,
                                folderRootUri = folderRootUri,
                                parentUri = directory.uri,
                                fileName = file.name,
                                lyricUri = lyricsByBaseName[baseName.lowercase(Locale.getDefault())]?.uri,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun isSupportedAudio(name: String): Boolean {
        return name.substringAfterLast(".", "").lowercase(Locale.US) in supportedExtensions
    }

    private fun parseTrackName(baseName: String): TrackNameParts {
        val cleanName = cleanTrackText(baseName)
        val segments = cleanName.split("-", "\u2013", "\u2014")
            .map { cleanTrackText(it) }
            .filter { it.isNotBlank() }
        val attachedFirst = segments.firstOrNull()?.let(::splitAttachedArtist)
        if (segments.size >= 2) {
            val first = segments.first()
            val second = segments[1]
            if (attachedFirst != null && (second.isLikelyAlbumOrDuplicateOf(attachedFirst) || second.isArtistAliasOf(attachedFirst.artist))) {
                return attachedFirst.withCleanArtistTitleOverlap()
            }
            if (first.isKnownArtistName()) {
                return TrackNameParts(title = second, artist = first).withCleanArtistTitleOverlap()
            }
            if (second.isKnownArtistName()) {
                return TrackNameParts(title = first, artist = second).withCleanArtistTitleOverlap()
            }
            if (second in knownAlbumNames && first.isNotBlank()) {
                return TrackNameParts(title = first, artist = "").withCleanArtistTitleOverlap()
            }
            if (first.length <= 10 && !first.hasLikelySongTitlePunctuation()) {
                return TrackNameParts(title = second, artist = first).withCleanArtistTitleOverlap()
            }
            return TrackNameParts(title = first, artist = second.takeIf { it !in knownAlbumNames }.orEmpty())
                .withCleanArtistTitleOverlap()
        }
        if (attachedFirst != null) return attachedFirst.withCleanArtistTitleOverlap()
        return TrackNameParts(title = cleanName, artist = "").withCleanArtistTitleOverlap()
    }

    private fun splitAttachedArtist(value: String): TrackNameParts? {
        val cleanValue = cleanTrackText(value)
        splitKnownArtistSuffix(cleanValue)?.let { return it.withCleanArtistTitleOverlap() }
        val artist = knownArtists
            .filter { cleanValue.endsWith(it) && cleanValue.length > it.length }
            .maxByOrNull { it.length }
        if (artist != null) {
            val title = cleanValue.removeSuffix(artist).trim()
            if (title.length >= 2) return TrackNameParts(title = title, artist = artist).withCleanArtistTitleOverlap()
        }
        val spaced = Regex("^(.+?)\\s+([^\\s]+)$").find(cleanValue) ?: return null
        val title = spaced.groupValues[1].trim()
        val right = spaced.groupValues[2].trim()
        if (title.isKnownArtistGroup() && right !in knownAlbumNames) {
            return TrackNameParts(title = right, artist = title).withCleanArtistTitleOverlap()
        }
        if (right.isKnownArtistName()) {
            return TrackNameParts(title = title, artist = right).withCleanArtistTitleOverlap()
        }
        splitKnownArtistSuffix(right)?.let { attached ->
            val cleanTitle = cleanTrackText(title)
            if (cleanTitle.length >= 2) {
                return TrackNameParts(title = cleanTitle, artist = attached.artist).withCleanArtistTitleOverlap()
            }
        }
        if (title.length >= 2 && right.length >= 2 && right !in knownAlbumNames) {
            return TrackNameParts(title = title, artist = right).withCleanArtistTitleOverlap()
        }
        return null
    }

    private fun splitKnownArtistSuffix(value: String): TrackNameParts? {
        val cleanValue = cleanTrackText(value)
        val artistSuffix = knownArtists
            .filter { cleanValue.endsWith(it) && cleanValue.length > it.length }
            .maxByOrNull { it.length }
            ?: return null
        val title = cleanValue.removeSuffix(artistSuffix).trim().withoutKnownAlbumTokens()
        val artistPrefix = title.takeLastKnownArtistSuffix()
        if (artistPrefix != null) {
            val realTitle = title.removeSuffix(artistPrefix).trimEnd('&', '\u3001', ',', '\uFF0C').trim()
            val artist = "$artistPrefix&$artistSuffix"
            if (realTitle.length >= 2) return TrackNameParts(title = realTitle, artist = artist).withCleanArtistTitleOverlap()
        }
        if (title.length >= 2) return TrackNameParts(title = title, artist = artistSuffix).withCleanArtistTitleOverlap()
        return null
    }

    private fun TrackNameParts.withCleanArtistTitleOverlap(): TrackNameParts {
        val cleanTitle = title.cleanComparable()
        if (cleanTitle.isBlank() || artist.isBlank()) return this
        if (!artist.cleanComparable().contains(cleanTitle)) return this
        val cleanArtist = artist
            .replace(title.trim(), "", ignoreCase = true)
            .trim(' ', '-', '\u2013', '\u2014', '&', '\u3001', ',', '\uFF0C')
        return copy(artist = cleanArtist)
    }

    private fun String.isKnownArtistName(): Boolean {
        val comparable = cleanComparable()
        return knownArtists.any { it.cleanComparableValue() == comparable } ||
            artistAliases.values.flatten().any { it.cleanComparableValue() == comparable }
    }

    private fun String.isKnownArtistGroup(): Boolean {
        val artists = split("&", "\u3001", "\uFF0C", ",", "/")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return artists.size >= 2 && artists.all { it.isKnownArtistName() }
    }

    private fun String.withoutKnownAlbumTokens(): String {
        return split(" ")
            .filter { token -> token.isNotBlank() && token !in knownAlbumNames }
            .joinToString(" ")
            .ifBlank { this }
    }

    private fun String.takeLastKnownArtistSuffix(): String? {
        return knownArtists
            .filter { endsWith(it) && length > it.length }
            .maxByOrNull { it.length }
    }

    private fun cleanTrackText(value: String): String {
        return value
            .replace(Regex("\u603b\\s*Music", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[_\\s-]+\\d{1,2}[-_:]\\d{2}[-_:]\\d{2}$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun String.hasLikelySongTitlePunctuation(): Boolean {
        return contains(" ") || contains("\u300a") || contains("\u300b") || contains("(") || contains("\uFF08")
    }

    private fun String.isUnknownArtist(): Boolean {
        val normalized = lowercase(Locale.getDefault())
        return isBlank() || this == "\u672a\u77e5\u6b4c\u624b" || normalized == "unknown artist"
    }

    private fun String.isLikelyAlbumOrDuplicateOf(parts: TrackNameParts): Boolean {
        val comparable = cleanComparable()
        return this in knownAlbumNames ||
            comparable == parts.title.cleanComparable() ||
            comparable == parts.artist.cleanComparable() ||
            comparable.contains(parts.title.cleanComparable()) ||
            comparable.contains(parts.artist.cleanComparable())
    }

    private fun String.isArtistAliasOf(artist: String): Boolean {
        val alias = this.cleanComparable()
        return artistAliases[artist.cleanComparable()].orEmpty().any { candidate -> alias == candidate.cleanComparableValue() }
    }

    private fun String.cleanComparable(): String {
        return cleanComparableValue()
    }

    private fun String.cleanComparableValue(): String {
        return lowercase(Locale.getDefault())
            .replace(Regex("[\\s_\\-\\u2013\\u2014&\u3001\uFF0C,\\.]+"), "")
            .trim()
    }

    private fun buildRenamedDisplayName(originalName: String, title: String): String {
        val extension = originalName.substringAfterLast(".", "").takeIf { it.isNotBlank() }
        if (extension == null || title.endsWith(".$extension", ignoreCase = true)) return title
        return "$title.$extension"
    }

    private fun findFolderDocument(track: MusicTrack): DocumentFile? {
        val parentUri = track.parentUri
        val fileName = track.fileName
        if (parentUri != null && !fileName.isNullOrBlank()) {
            DocumentFile.fromTreeUri(context, parentUri)
                ?.findFile(fileName)
                ?.let { return it }
        }
        return DocumentFile.fromSingleUri(context, track.uri)
    }

    private fun String.toSafeFileBaseName(): String {
        return replace(Regex("[\\\\/:*?\"<>|]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun lyricKey(relativePath: String, baseName: String): String {
        return "${relativePath.trim('/')}/${baseName}".lowercase(Locale.getDefault())
    }

    private fun readMetadata(uri: Uri): TrackMetadata {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val metadata = TrackMetadata(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty(),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty(),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty(),
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
            )
            retriever.release()
            metadata
        }.getOrDefault(TrackMetadata())
    }

    private fun readEmbeddedLyrics(uri: Uri): String? {
        return runCatching {
            val key = MediaMetadataRetriever::class.java.getField("METADATA_KEY_LYRIC").getInt(null)
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val lyrics = retriever.extractMetadata(key)
            retriever.release()
            lyrics
        }.getOrNull()
    }

    private fun readFlacLyrics(uri: Uri): String? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                if (input.readAscii(4) != "fLaC") return@use null
                var isLastBlock = false
                while (!isLastBlock) {
                    val header = input.readNBytesCompat(4)
                    if (header.size < 4) return@use null
                    isLastBlock = (header[0].toInt() and 0x80) != 0
                    val blockType = header[0].toInt() and 0x7F
                    val length = ((header[1].toInt() and 0xFF) shl 16) or
                        ((header[2].toInt() and 0xFF) shl 8) or
                        (header[3].toInt() and 0xFF)
                    if (length <= 0 || length > MAX_FLAC_METADATA_BLOCK_SIZE) return@use null
                    val block = input.readNBytesCompat(length)
                    if (block.size < length) return@use null
                    if (blockType == FLAC_VORBIS_COMMENT_BLOCK) {
                        return@use parseVorbisLyrics(block)
                    }
                }
                null
            }
        }.getOrNull()
    }

    private fun parseVorbisLyrics(block: ByteArray): String? {
        val buffer = ByteBuffer.wrap(block).order(ByteOrder.LITTLE_ENDIAN)
        if (buffer.remaining() < 4) return null
        val vendorLength = buffer.int
        if (vendorLength < 0 || vendorLength > buffer.remaining()) return null
        buffer.position(buffer.position() + vendorLength)
        if (buffer.remaining() < 4) return null
        val count = buffer.int
        repeat(count.coerceAtMost(512)) {
            if (buffer.remaining() < 4) return null
            val length = buffer.int
            if (length < 0 || length > buffer.remaining()) return null
            val value = String(block, buffer.position(), length, Charsets.UTF_8)
            buffer.position(buffer.position() + length)
            val key = value.substringBefore("=", "").uppercase(Locale.US)
            val text = value.substringAfter("=", "")
            if (key in lyricCommentKeys && text.isNotBlank()) return text
        }
        return null
    }

    private fun InputStream.readAscii(length: Int): String? {
        val bytes = readNBytesCompat(length)
        if (bytes.size < length) return null
        return String(bytes, Charsets.US_ASCII)
    }

    private fun InputStream.readNBytesCompat(length: Int): ByteArray {
        val buffer = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = read(buffer, offset, length - offset)
            if (read == -1) break
            offset += read
        }
        return if (offset == length) buffer else buffer.copyOf(offset)
    }

    private data class TrackMetadata(
        val title: String = "",
        val artist: String = "",
        val album: String = "",
        val durationMs: Long = 0L,
    )

    private data class TrackNameParts(
        val title: String,
        val artist: String,
    )

    companion object {
        private val supportedExtensions = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav")
        private const val FLAC_VORBIS_COMMENT_BLOCK = 4
        private const val MAX_FLAC_METADATA_BLOCK_SIZE = 4 * 1024 * 1024
        private val lyricCommentKeys = setOf("LYRICS", "UNSYNCEDLYRICS", "SYNCEDLYRICS", "UNSYNCED LYRICS")
        private val knownAlbumNames = setOf(
            "\u53f6\u60e0\u7f8e",
            "\u730e\u6237\u661f\u5ea7",
            "\u6a59\u6708",
            "\u4eb2\u5bc6\u7231\u4eba",
            "\u5b59\u71d5\u59ff\u540c\u540d\u4e13\u8f91",
            "\u7f57\u66fc\u8482\u514b\u7684\u7231\u60c5\u8d3a\u656c\u8f69",
            "\u6587\u7231CG",
            "G.E.M.",
        )
        private val knownArtists = listOf(
            "\u5468\u6770\u4f26",
            "\u738b\u529b\u5b8f",
            "\u9093\u7d2b\u68cb",
            "\u8303\u73ae\u742a",
            "\u5468\u6df1",
            "\u5f20\u97f6\u6db5",
            "\u6885\u8273\u82b3",
            "\u5f20\u6770",
            "\u5b59\u71d5\u59ff",
            "\u6d1b\u5929\u4f9d",
            "ilem",
            "\u738b\u5ffb\u8fb0",
            "\u82cf\u661f\u5a55",
            "\u8d3a\u656c\u8f69",
            "\u6797\u4fca\u6770",
            "\u90ed\u6e90\u6f6e",
            "\u5b8b\u51ac\u91ce",
            "\u6734\u6811",
            "\u8bb8\u4e00\u9e23",
            "\u65b9\u5927\u540c",
            "G.E.M.",
            "GEM",
        )
        private val artistAliases = mapOf(
            "\u9093\u7d2b\u68cb".lowercase(Locale.getDefault()) to listOf("G.E.M.", "GEM"),
        )
    }
}

enum class FileRenameResult {
    SUCCESS,
    UNSUPPORTED_SOURCE,
    NO_PERMISSION,
    FAILED,
}

enum class FileDeleteResult {
    SUCCESS,
    UNSUPPORTED_SOURCE,
    NO_PERMISSION,
    FAILED,
}
