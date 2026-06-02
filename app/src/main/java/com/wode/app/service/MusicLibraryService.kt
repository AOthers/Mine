package com.wode.app.service

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.wode.app.data.MusicSource
import com.wode.app.data.MusicTrack
import java.util.Locale

class MusicLibraryService(private val context: Context) {
    fun loadLibrary(folderUris: List<Uri>, includeSystemLibrary: Boolean): List<MusicTrack> {
        val tracks = buildList {
            if (includeSystemLibrary) addAll(loadSystemTracks())
            folderUris.forEach { addAll(loadFolderTracks(it)) }
        }
        return tracks.distinctBy { it.uri.toString() }
            .sortedWith(compareBy({ it.title.lowercase(Locale.getDefault()) }, { it.displayArtist }))
    }

    fun readLyrics(track: MusicTrack): String? {
        track.lyricUri?.let { uri ->
            return runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
        }
        return readEmbeddedLyrics(track.uri)
    }

    private fun loadSystemTracks(): List<MusicTrack> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
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
                buildList {
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        add(
                            MusicTrack(
                                id = "system:$id",
                                title = cursor.getString(titleCol).orEmpty().ifBlank { "\u672a\u77e5\u6b4c\u66f2" },
                                artist = cursor.getString(artistCol).orEmpty(),
                                album = cursor.getString(albumCol).orEmpty(),
                                durationMs = cursor.getLong(durationCol),
                                uri = ContentUris.withAppendedId(collection, id),
                                source = MusicSource.SYSTEM,
                            ),
                        )
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun loadFolderTracks(treeUri: Uri): List<MusicTrack> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        return scanDirectory(root, root.name ?: "\u81ea\u9009\u6587\u4ef6\u5939")
    }

    private fun scanDirectory(directory: DocumentFile, folderName: String): List<MusicTrack> {
        val children = directory.listFiles()
        val lyricsByBaseName = children
            .filter { it.isFile && it.name?.endsWith(".lrc", ignoreCase = true) == true }
            .associateBy { it.name.orEmpty().substringBeforeLast(".").lowercase(Locale.getDefault()) }

        return buildList {
            children.forEach { file ->
                when {
                    file.isDirectory -> addAll(scanDirectory(file, folderName))
                    file.isFile && isSupportedAudio(file.name.orEmpty()) -> {
                        val metadata = readMetadata(file.uri)
                        val baseName = file.name.orEmpty().substringBeforeLast(".")
                        add(
                            MusicTrack(
                                id = "folder:${file.uri}",
                                title = metadata.title.ifBlank { baseName.ifBlank { "\u672a\u77e5\u6b4c\u66f2" } },
                                artist = metadata.artist,
                                album = metadata.album,
                                durationMs = metadata.durationMs,
                                uri = file.uri,
                                source = MusicSource.FOLDER,
                                folderName = folderName,
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

    private data class TrackMetadata(
        val title: String = "",
        val artist: String = "",
        val album: String = "",
        val durationMs: Long = 0L,
    )

    companion object {
        private val supportedExtensions = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav")
    }
}
