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
    private val queuesFile = File(ctx.filesDir, "queues.json")
    private val cacheFile = File(ctx.filesDir, "tracks_cache.json")
    private val addedFile = File(ctx.filesDir, "added.json")

    private val audioExt = setOf("mp3", "flac", "ogg", "oga", "opus", "m4a", "aac", "wav", "wma", "mka", "amr")

    // ---------- Папка ----------

    fun savedFolder(): Uri? = prefs.getString("folder", null)?.let { Uri.parse(it) }

    fun saveFolder(uri: Uri?) {
        prefs.edit().putString("folder", uri?.toString()).apply()
    }

    // ---------- Тема ----------

    fun loadTheme(): ThemeMode =
        runCatching { ThemeMode.valueOf(prefs.getString("theme", null) ?: "") }.getOrDefault(ThemeMode.SYSTEM)

    fun saveTheme(mode: ThemeMode) {
        prefs.edit().putString("theme", mode.name).apply()
    }

    // ---------- Сканирование ----------

    private class Entry(val uri: Uri, val name: String, val stamp: String, val size: Long, val modified: Long)

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
                    if (cached != null) key to cached.copy(modified = e.modified)
                    else key to gate.withPermit { readTrack(e) }
                }
            }.awaitAll()
        }

        saveCache(pairs)

        // Дата «добавлен в плеер»: запоминаем, когда трек впервые увидели при сканировании
        val added = loadAdded()
        val now = System.currentTimeMillis()
        var changed = false
        val result = pairs.map { (_, t) ->
            t.copy(added = added.getOrPut(t.uri) { changed = true; now })
        }
        if (changed) saveAdded(added)
        result.sortedBy { it.title.lowercase() }
    }

    private fun loadAdded(): HashMap<String, Long> = try {
        val o = JSONObject(addedFile.readText())
        HashMap<String, Long>().also { m -> o.keys().forEach { k -> m[k] = o.getLong(k) } }
    } catch (_: Exception) {
        HashMap()
    }

    private fun saveAdded(map: Map<String, Long>) {
        try {
            val o = JSONObject()
            for ((k, v) in map) o.put(k, v)
            addedFile.writeText(o.toString())
        } catch (_: Exception) {
        }
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
                    // Скрытые файлы и папки (начинаются с точки) — не музыка. Главное: Android при удалении файла
                    // не стирает его сразу, а переименовывает в «.trashed-<время>-<имя>» (корзина), также бывают «.pending-…».
                    if (name.startsWith(".")) continue
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        walk(tree, id, out)
                    } else if (mime.startsWith("audio/") || name.substringAfterLast('.', "").lowercase() in audioExt) {
                        out += Entry(
                            DocumentsContract.buildDocumentUriUsingTree(tree, id),
                            name,
                            "${c.getLong(3)}|${c.getLong(4)}",
                            c.getLong(4),
                            c.getLong(3),
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
                modified = e.modified,
            )
        } catch (_: Exception) {
            Track(
                e.uri.toString(), fallbackTitle, NO_ARTIST, NO_ARTIST, NO_ALBUM, 0L, 0,
                info = runCatching { AudioProbe.probe(ctx, e.uri, e.name, e.size, 0L, null) }
                    .getOrDefault(AudioInfo()),
                modified = e.modified,
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
                        o.optLong("mod", 0L),
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
                        .put("bits", t.info.bitsPerSample).put("ll", t.info.lossless)
                        .put("mod", t.modified),
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
    fun cachedTracks(): List<Track> {
        val added = loadAdded()
        return loadCache().values.map { it.copy(added = added[it.uri] ?: 0L) }.sortedBy { it.title.lowercase() }
    }

    // ---------- Плейлисты ----------

    fun loadPlaylists(): List<Playlist> = try {
        val arr = JSONArray(playlistsFile.readText())
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val uris = o.getJSONArray("tracks")
            Playlist(
                o.getString("id"), o.getString("name"), List(uris.length()) { uris.getString(it) },
                o.optLong("created", 0L), o.optLong("updated", 0L),
            )
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
                        .put("tracks", JSONArray(p.trackUris))
                        .put("created", p.createdAt).put("updated", p.updatedAt),
                )
            }
            playlistsFile.writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    // ---------- Очереди ----------

    /** Всегда возвращает ровно [QUEUE_COUNT] очередей: недостающие создаются пустыми, лишние отбрасываются. */
    fun loadQueues(): List<PlayQueue> {
        val byId = HashMap<Int, List<String>>()
        try {
            val arr = JSONArray(queuesFile.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val uris = o.getJSONArray("tracks")
                byId[o.getInt("id")] = List(uris.length()) { uris.getString(it) }.distinct()
            }
        } catch (_: Exception) {
        }
        return List(QUEUE_COUNT) { PlayQueue(it, byId[it] ?: emptyList()) }
    }

    fun saveQueues(list: List<PlayQueue>) {
        try {
            val arr = JSONArray()
            for (q in list) {
                arr.put(JSONObject().put("id", q.id).put("tracks", JSONArray(q.trackUris)))
            }
            queuesFile.writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    fun loadSelectedQueue(): Int = prefs.getInt("queue_selected", 0).coerceIn(0, QUEUE_COUNT - 1)

    fun saveSelectedQueue(index: Int) {
        prefs.edit().putInt("queue_selected", index).apply()
    }

    // ---------- Сортировка и ручной порядок ----------

    fun loadSort(tab: String, default: SortState, allowed: List<SortKey>): SortState {
        val raw = prefs.getString("sort_$tab", null) ?: return default
        val key = runCatching { SortKey.valueOf(raw.substringBefore(':')) }.getOrNull()
        if (key == null || key !in allowed) return default
        return SortState(key, raw.substringAfter(':', "0") == "1")
    }

    fun saveSort(tab: String, s: SortState) {
        prefs.edit().putString("sort_$tab", "${s.key.name}:${if (s.descending) 1 else 0}").apply()
    }

    /** Порядок, заданный перетаскиванием (идентификаторы треков, альбомов или плейлистов). */
    /** Сбрасывает сортировки всех вкладок и ручной порядок (перетаскивание). */
    fun resetViewSettings() {
        val e = prefs.edit()
        prefs.all.keys.filter { it.startsWith("sort_") }.forEach { e.remove(it) }
        e.apply()
        ctx.filesDir.listFiles()?.filter { it.name.startsWith("order_") }?.forEach { it.delete() }
    }

    fun loadOrder(name: String): List<String> = try {
        val arr = JSONArray(File(ctx.filesDir, "order_$name.json").readText())
        List(arr.length()) { arr.getString(it) }
    } catch (_: Exception) {
        emptyList()
    }

    fun saveOrder(name: String, order: List<String>) {
        try {
            File(ctx.filesDir, "order_$name.json").writeText(JSONArray(order).toString())
        } catch (_: Exception) {
        }
    }
}
