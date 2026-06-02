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
                        add(
                            MusicTrack(
                                id = "system:$id",
                                title = cursor.getString(titleCol).orEmpty().ifBlank { baseName.ifBlank { "\u672a\u77e5\u6b4c\u66f2" } },
                                artist = cursor.getString(artistCol).orEmpty(),
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
                        add(
                            MusicTrack(
                                id = "folder:${file.uri}",
                                title = baseName.ifBlank { "\u672a\u77e5\u6b4c\u66f2" },
                                artist = "",
                                album = "",
                                durationMs = 0L,
                                modifiedTimeMs = file.lastModified(),
                                uri = file.uri,
                                source = MusicSource.FOLDER,
                                folderName = folderName,
                                folderRootUri = folderRootUri,
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

    companion object {
        private val supportedExtensions = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav")
        private const val FLAC_VORBIS_COMMENT_BLOCK = 4
        private const val MAX_FLAC_METADATA_BLOCK_SIZE = 4 * 1024 * 1024
        private val lyricCommentKeys = setOf("LYRICS", "UNSYNCEDLYRICS", "SYNCEDLYRICS", "UNSYNCED LYRICS")
    }
}
