package com.example.localplayer

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Хранение настроек/плейлистов и сканирование выбранной папки.
 * Сканируется ТОЛЬКО поддерево, которое пользователь выбрал в настройках (Storage Access Framework).
 */
class Library(private val ctx: Context) {

    private val prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val playlistsFile = File(ctx.filesDir, "playlists.json")
    private val cacheFile = File(ctx.filesDir, "tracks_cache.json")

    private val audioExt = setOf("mp3", "flac", "ogg", "oga", "opus", "m4a", "aac", "wav", "wma", "mka", "amr")

    // ---------- Папка ----------

    fun savedFolder(): Uri? = prefs.getString("folder", null)?.let { Uri.parse(it) }

    fun saveFolder(uri: Uri?) {
        prefs.edit().putString("folder", uri?.toString()).apply()
    }

    // ---------- Сканирование ----------

    private class Entry(val uri: Uri, val name: String, val stamp: String, val size: Long)

    suspend fun scan(tree: Uri): List<Track> = withContext(Dispatchers.IO) {
        val files = ArrayList<Entry>()
        walk(tree, DocumentsContract.getTreeDocumentId(tree), files)

        val cache = loadCache()
        val gate = Semaphore(4)

        val pairs: List<Pair<String, Track>> = coroutineScope {
            files.map { e ->
                async {
                    val key = "${e.uri}|${e.stamp}"
                    val cached = cache[key]
                    if (cached != null) key to cached
                    else key to gate.withPermit { readTrack(e) }
                }
            }.awaitAll()
        }

        saveCache(pairs)
        pairs.map { it.second }.sortedBy { it.title.lowercase() }
    }

    private fun walk(tree: Uri, parentDocId: String, out: MutableList<Entry>) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        try {
            ctx.contentResolver.query(children, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: ""
                    val mime = c.getString(2) ?: ""
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        walk(tree, id, out)
                    } else if (mime.startsWith("audio/") || name.substringAfterLast('.', "").lowercase() in audioExt) {
                        out += Entry(
                            DocumentsContract.buildDocumentUriUsingTree(tree, id),
                            name,
                            "${c.getLong(3)}|${c.getLong(4)}",
                            c.getLong(4),
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // нет доступа к подпапке — пропускаем
        }
    }

    private fun readTrack(e: Entry): Track {
        val fallbackTitle = e.name.substringBeforeLast('.')
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, e.uri)
            fun m(k: Int) = r.extractMetadata(k)?.trim()?.takeIf { it.isNotEmpty() }
            val artist = m(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: NO_ARTIST
            val duration = m(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val info = runCatching { AudioProbe.probe(ctx, e.uri, e.name, e.size, duration, r) }
                .getOrDefault(AudioInfo())
            Track(
                uri = e.uri.toString(),
                title = m(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: fallbackTitle,
                artist = artist,
                albumArtist = m(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST) ?: artist,
                album = m(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: NO_ALBUM,
                durationMs = duration,
                trackNo = m(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                    ?.substringBefore('/')?.trim()?.toIntOrNull() ?: 0,
                info = info,
            )
        } catch (_: Exception) {
            Track(
                e.uri.toString(), fallbackTitle, NO_ARTIST, NO_ARTIST, NO_ALBUM, 0L, 0,
                info = runCatching { AudioProbe.probe(ctx, e.uri, e.name, e.size, 0L, null) }
                    .getOrDefault(AudioInfo()),
            )
        } finally {
            runCatching { r.release() }
        }
    }

    // ---------- Кэш метаданных ----------

    private fun loadCache(): Map<String, Track> = try {
        val arr = JSONArray(cacheFile.readText())
        buildMap {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                put(
                    o.getString("k"),
                    Track(
                        o.getString("uri"), o.getString("title"), o.getString("artist"),
                        o.getString("albumArtist"), o.getString("album"),
                        o.getLong("dur"), o.getInt("no"),
                        // Нет поля «fmt» (кэш старой версии) — бросит исключение, и папка просканируется заново
                        AudioInfo(
                            o.getString("fmt"), o.getInt("kbps"), o.getInt("hz"),
                            o.getInt("bits"), o.getBoolean("ll"),
                        ),
                    ),
                )
            }
        }
    } catch (_: Exception) {
        emptyMap()
    }

    private fun saveCache(pairs: List<Pair<String, Track>>) {
        try {
            val arr = JSONArray()
            for ((k, t) in pairs) {
                arr.put(
                    JSONObject()
                        .put("k", k).put("uri", t.uri).put("title", t.title).put("artist", t.artist)
                        .put("albumArtist", t.albumArtist).put("album", t.album)
                        .put("dur", t.durationMs).put("no", t.trackNo)
                        .put("fmt", t.info.format).put("kbps", t.info.bitrateKbps).put("hz", t.info.sampleRateHz)
                        .put("bits", t.info.bitsPerSample).put("ll", t.info.lossless),
                )
            }
            cacheFile.writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    fun clearCache() {
        cacheFile.delete()
    }

    /** Треки из прошлого сканирования: показываем их мгновенно, пока идёт актуальный скан. */
    fun cachedTracks(): List<Track> = loadCache().values.sortedBy { it.title.lowercase() }

    // ---------- Плейлисты ----------

    fun loadPlaylists(): List<Playlist> = try {
        val arr = JSONArray(playlistsFile.readText())
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val uris = o.getJSONArray("tracks")
            Playlist(o.getString("id"), o.getString("name"), List(uris.length()) { uris.getString(it) })
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun savePlaylists(list: List<Playlist>) {
        try {
            val arr = JSONArray()
            for (p in list) {
                arr.put(
                    JSONObject().put("id", p.id).put("name", p.name)
                        .put("tracks", JSONArray(p.trackUris)),
                )
            }
            playlistsFile.writeText(arr.toString())
        } catch (_: Exception) {
        }
    }
}
