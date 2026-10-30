package com.example.localplayer

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Строка текста песни с моментом начала (для текста с таймкодами). */
data class LyricLine(val timeMs: Long, val text: String)

/** Результат загрузки текста для трека. */
sealed interface LyricsResult {
    /** Текста нет ни в тегах, ни в .lrc/.txt. */
    data object None : LyricsResult

    /** Текст без таймкодов. Пустая строка в списке — разрыв между куплетами. */
    data class Plain(val lines: List<String>) : LyricsResult

    /** Текст с таймкодами, отсортирован по времени. */
    data class Synced(val lines: List<LyricLine>) : LyricsResult
}

/** Разбор текста песни: формат LRC (с таймкодами) или простой текст. */
object LyricsParser {
    private val TIME = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?\]""")
    private val WORD_TAG = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")
    private val META = Regex("""^\[([A-Za-z#]+):(.*)\]$""")

    fun parse(raw: String): LyricsResult {
        val text = raw.replace("\r\n", "\n").replace('\r', '\n').removePrefix("\uFEFF")
        val stamped = ArrayList<LyricLine>()
        val plain = ArrayList<String>()
        var offset = 0L

        for (rawLine in text.split('\n')) {
            val line = rawLine.trim()
            val meta = META.find(line)
            if (meta != null) {
                // [offset:+500], [ti:...], [ar:...] — служебные теги, в текст не попадают
                if (meta.groupValues[1].lowercase() == "offset") {
                    offset = meta.groupValues[2].trim().removePrefix("+").toLongOrNull() ?: 0L
                }
                continue
            }
            var rest = line
            val stamps = ArrayList<Long>()
            while (true) {
                val m = TIME.find(rest)
                if (m == null || m.range.first != 0) break
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val frac = m.groupValues[3]
                val ms = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.take(3).toLong()
                }
                stamps += (min * 60 + sec) * 1000 + ms
                rest = rest.substring(m.range.last + 1)
            }
            if (stamps.isNotEmpty()) {
                val body = WORD_TAG.replace(rest, "").trim()
                for (s in stamps) stamped += LyricLine((s - offset).coerceAtLeast(0L), body)
            } else {
                plain += line
            }
        }

        if (stamped.isNotEmpty()) {
            val sorted = stamped.sortedBy { it.timeMs } // сортировка устойчивая: порядок строк с одним временем сохраняется
            return if (sorted.any { it.text.isNotEmpty() }) LyricsResult.Synced(sorted) else LyricsResult.None
        }
        return normalizePlain(plain)
    }

    /** Убирает пустые строки в начале и конце, а подряд идущие пустые сводит к одной. */
    fun normalizePlain(lines: List<String>): LyricsResult {
        val out = ArrayList<String>()
        for (l in lines) {
            if (l.isEmpty() && (out.isEmpty() || out.last().isEmpty())) continue
            out += l
        }
        while (out.isNotEmpty() && out.last().isEmpty()) out.removeAt(out.lastIndex)
        return if (out.isEmpty()) LyricsResult.None else LyricsResult.Plain(out)
    }
}

/** Текст, найденный в тегах файла. */
class TagLyrics(val text: String?, val synced: List<LyricLine>?)

/** Чтение текста из тегов: ID3v2 (MP3 и др.), FLAC, Ogg Vorbis/Opus, MP4/M4A. Файл читается выборочно, обложки пропускаются. */
object LyricsTags {
    private const val MAX_FRAME = 2 * 1024 * 1024
    private const val MAX_OGG = 8L * 1024 * 1024

    /** Есть ли в тегах пригодный текст. Вызывается при сканировании папки. */
    fun hasLyrics(ctx: Context, uri: Uri): Boolean = runCatching {
        val t = read(ctx, uri) ?: return@runCatching false
        if (!t.synced.isNullOrEmpty()) return@runCatching true
        val text = t.text ?: return@runCatching false
        LyricsParser.parse(text) !is LyricsResult.None
    }.getOrDefault(false)

    fun read(ctx: Context, uri: Uri): TagLyrics? = runCatching {
        val pfd = ctx.contentResolver.openFileDescriptor(uri, "r") ?: return@runCatching null
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { stream ->
            val ch = stream.channel
            val head = ch.readAt(0, 12) ?: return@use null
            val magic3 = String(head, 0, 3, Charsets.ISO_8859_1)
            val magic4 = String(head, 0, 4, Charsets.ISO_8859_1)
            when {
                magic3 == "ID3" -> readId3(ch)
                magic4 == "fLaC" -> readFlac(ch)
                magic4 == "OggS" -> readOgg(ch)
                String(head, 4, 4, Charsets.ISO_8859_1) == "ftyp" -> readMp4(ch)
                else -> null
            }
        }
    }.getOrNull()

    // ---------- Общие помощники ----------

    private fun FileChannel.readAt(pos: Long, len: Int): ByteArray? {
        if (pos < 0 || len < 0 || len > MAX_FRAME * 4) return null
        val buf = ByteBuffer.allocate(len)
        var p = pos
        while (buf.hasRemaining()) {
            val n = read(buf, p)
            if (n <= 0) return null
            p += n
        }
        return buf.array()
    }

    private fun beInt(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
            ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)

    private fun leInt(b: ByteArray, o: Int): Int =
        ((b[o + 3].toInt() and 0xFF) shl 24) or ((b[o + 2].toInt() and 0xFF) shl 16) or
            ((b[o + 1].toInt() and 0xFF) shl 8) or (b[o].toInt() and 0xFF)

    private fun synchsafe(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0x7F) shl 21) or ((b[o + 1].toInt() and 0x7F) shl 14) or
            ((b[o + 2].toInt() and 0x7F) shl 7) or (b[o + 3].toInt() and 0x7F)

    private fun deUnsync(b: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(b.size)
        var i = 0
        while (i < b.size) {
            out.write(b[i].toInt())
            if (b[i] == 0xFF.toByte() && i + 1 < b.size && b[i + 1] == 0.toByte()) i++
            i++
        }
        return out.toByteArray()
    }

    /** Текст в «старой» кодировке: русские теги часто лежат в windows-1251, хотя помечены как ISO-8859-1. */
    fun decodeLegacy(b: ByteArray, off: Int = 0, len: Int = b.size - off): String {
        var high = 0
        var letters = 0
        for (i in off until off + len) {
            val v = b[i].toInt() and 0xFF
            if (v >= 0x80) { high++; letters++ }
            else if ((v in 0x41..0x5A) || (v in 0x61..0x7A)) letters++
        }
        val cs: Charset = if (high > 0 && high.toDouble() / letters > 0.3) Charset.forName("windows-1251") else Charsets.ISO_8859_1
        return String(b, off, len, cs)
    }

    /** Текст файла .lrc/.txt: BOM, UTF-8, иначе windows-1251 / Latin-1. */
    fun decodeFile(b: ByteArray): String {
        if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte())
            return String(b, 3, b.size - 3, Charsets.UTF_8)
        if (b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte()) return String(b, 2, b.size - 2, Charsets.UTF_16LE)
        if (b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte()) return String(b, 2, b.size - 2, Charsets.UTF_16BE)
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(b)).toString()
        } catch (_: CharacterCodingException) {
            decodeLegacy(b)
        }
    }

    // ---------- ID3v2 ----------

    private fun id3Decode(b: ByteArray, off: Int, end: Int, enc: Int): String {
        if (end <= off) return ""
        return when (enc) {
            1 -> String(b, off, end - off, Charsets.UTF_16) // BOM определяет порядок байт
            2 -> String(b, off, end - off, Charsets.UTF_16BE)
            3 -> String(b, off, end - off, Charsets.UTF_8)
            else -> decodeLegacy(b, off, end - off)
        }
    }

    /** Индекс начала терминатора строки начиная с [from]; для UTF-16 ищет пару нулей на чётной границе. */
    private fun termIndex(b: ByteArray, from: Int, enc: Int): Int {
        if (enc == 1 || enc == 2) {
            var i = from
            while (i + 1 < b.size) {
                if (b[i] == 0.toByte() && b[i + 1] == 0.toByte()) return i
                i += 2
            }
            return -1
        }
        for (i in from until b.size) if (b[i] == 0.toByte()) return i
        return -1
    }

    private fun termLen(enc: Int) = if (enc == 1 || enc == 2) 2 else 1

    private fun readId3(ch: FileChannel): TagLyrics? {
        val h = ch.readAt(0, 10) ?: return null
        val ver = h[3].toInt()
        if (ver !in 2..4) return null
        val flags = h[5].toInt()
        val tagSize = synchsafe(h, 6).toLong()
        val end = minOf(10L + tagSize, ch.size())
        val tagUnsync = (flags and 0x80) != 0

        var pos = 10L
        if (ver >= 3 && (flags and 0x40) != 0) {
            val e = ch.readAt(pos, 4) ?: return null
            pos += if (ver == 4) synchsafe(e, 0).toLong() else beInt(e, 0).toLong() + 4
        }
        val hdrLen = if (ver == 2) 6 else 10
        var text: String? = null
        var synced: List<LyricLine>? = null

        while (pos + hdrLen <= end) {
            val fh = ch.readAt(pos, hdrLen) ?: break
            if (fh[0] == 0.toByte()) break // дальше только заполнение
            val id = String(fh, 0, if (ver == 2) 3 else 4, Charsets.ISO_8859_1)
            val size = when (ver) {
                2 -> ((fh[3].toInt() and 0xFF) shl 16) or ((fh[4].toInt() and 0xFF) shl 8) or (fh[5].toInt() and 0xFF)
                3 -> beInt(fh, 4)
                else -> synchsafe(fh, 4)
            }
            if (size <= 0 || pos + hdrLen + size > end) break
            val wanted = id == "USLT" || id == "ULT" || id == "SYLT" || id == "SLT"
            if (wanted && size <= MAX_FRAME) {
                val f2 = if (ver >= 3) fh[9].toInt() and 0xFF else 0
                val skip = when (ver) {
                    3 -> (f2 and 0xC0) != 0 // сжатие или шифрование — не читаем
                    4 -> (f2 and 0x0C) != 0
                    else -> false
                }
                if (!skip) {
                    var body = ch.readAt(pos + hdrLen, size)
                    if (body != null) {
                        var start = 0
                        if (ver == 3 && (f2 and 0x20) != 0) start += 1 // байт группы
                        if (ver == 4) {
                            if ((f2 and 0x40) != 0) start += 1
                            if ((f2 and 0x01) != 0) start += 4 // индикатор длины данных
                        }
                        if (start > 0) body = if (start < body.size) body.copyOfRange(start, body.size) else null
                        val unsync = tagUnsync && ver <= 3 || (ver == 4 && (f2 and 0x02) != 0)
                        if (body != null && unsync) body = deUnsync(body)
                        if (body != null) {
                            if (id == "USLT" || id == "ULT") {
                                if (text == null) text = parseUslt(body)
                            } else if (synced == null) {
                                synced = parseSylt(body)
                            }
                        }
                    }
                }
            }
            pos += hdrLen + size
        }
        return if (text == null && synced == null) null else TagLyrics(text, synced)
    }

    private fun parseUslt(b: ByteArray): String? {
        if (b.size < 5) return null
        val enc = b[0].toInt() and 0xFF
        val t = termIndex(b, 4, enc)
        if (t < 0) return null
        val from = t + termLen(enc)
        return id3Decode(b, from, b.size, enc).trim('\u0000').takeIf { it.isNotBlank() }
    }

    private fun parseSylt(b: ByteArray): List<LyricLine>? {
        if (b.size < 7) return null
        val enc = b[0].toInt() and 0xFF
        val format = b[4].toInt() and 0xFF
        if (format != 2) return null // метки в MPEG-кадрах без карты кадров не перевести во время
        val d = termIndex(b, 6, enc)
        if (d < 0) return null
        var pos = d + termLen(enc)
        val entries = ArrayList<Pair<Long, String>>()
        while (pos < b.size) {
            val t = termIndex(b, pos, enc)
            if (t < 0 || t + termLen(enc) + 4 > b.size) break
            val s = id3Decode(b, pos, t, enc)
            val tp = t + termLen(enc)
            entries += (beInt(b, tp).toLong() and 0xFFFFFFFFL) to s
            pos = tp + 4
        }
        if (entries.isEmpty()) return null

        val lines = ArrayList<LyricLine>()
        if (entries.none { it.second.contains('\n') }) {
            // Каждая запись — готовая строка
            for ((t, s) in entries) {
                val x = s.replace("\r", "").trim()
                if (x.isNotEmpty()) lines += LyricLine(t, x)
            }
        } else {
            // Записи — слоги/слова, строки разделены переводами строки
            val sb = StringBuilder()
            var start = -1L
            fun flush() {
                val x = sb.toString().trim()
                if (start >= 0 && x.isNotEmpty()) lines += LyricLine(start, x)
                sb.setLength(0)
                start = -1L
            }
            for ((t, s) in entries) {
                val parts = s.replace("\r", "").split('\n')
                for ((i, p) in parts.withIndex()) {
                    if (i > 0) flush()
                    if (p.isNotEmpty()) {
                        if (start < 0) start = t
                        sb.append(p)
                    }
                }
            }
            flush()
        }
        return lines.sortedBy { it.timeMs }.takeIf { it.isNotEmpty() }
    }

    // ---------- Vorbis comments (FLAC, Ogg) ----------

    private fun vorbisLyrics(b: ByteArray, startOff: Int): TagLyrics? {
        var p = startOff
        if (p + 4 > b.size) return null
        p += 4 + leInt(b, p) // производитель
        if (p < 0 || p + 4 > b.size) return null
        val count = leInt(b, p)
        p += 4
        var text: String? = null
        var i = 0
        while (i < count && p + 4 <= b.size) {
            val len = leInt(b, p)
            p += 4
            if (len < 0 || p + len > b.size) break
            val eq = (p until p + len).firstOrNull { b[it] == '='.code.toByte() }
            if (eq != null) {
                val key = String(b, p, eq - p, Charsets.US_ASCII).uppercase()
                if (key == "LYRICS" || key == "UNSYNCEDLYRICS" || key == "SYNCEDLYRICS" || key == "LYRICS_SYNCED") {
                    val v = String(b, eq + 1, p + len - eq - 1, Charsets.UTF_8)
                    if (v.isNotBlank() && text == null) text = v
                }
            }
            p += len
            i++
        }
        return text?.let { TagLyrics(it, null) }
    }

    private fun readFlac(ch: FileChannel): TagLyrics? {
        var pos = 4L
        val size = ch.size()
        while (pos + 4 <= size) {
            val h = ch.readAt(pos, 4) ?: return null
            val last = (h[0].toInt() and 0x80) != 0
            val type = h[0].toInt() and 0x7F
            val len = ((h[1].toInt() and 0xFF) shl 16) or ((h[2].toInt() and 0xFF) shl 8) or (h[3].toInt() and 0xFF)
            if (type == 4) {
                if (len > MAX_FRAME * 2) return null
                val b = ch.readAt(pos + 4, len) ?: return null
                return vorbisLyrics(b, 0)
            }
            if (last) return null
            pos += 4 + len
        }
        return null
    }

    private fun readOgg(ch: FileChannel): TagLyrics? {
        val cur = ByteArrayOutputStream()
        var pos = 0L
        var packets = 0
        var total = 0L
        while (total < MAX_OGG) {
            val h = ch.readAt(pos, 27) ?: return null
            if (String(h, 0, 4, Charsets.ISO_8859_1) != "OggS") return null
            val segs = h[26].toInt() and 0xFF
            val table = ch.readAt(pos + 27, segs) ?: return null
            var bodyLen = 0
            for (s in table) bodyLen += s.toInt() and 0xFF
            val body = ch.readAt(pos + 27 + segs, bodyLen) ?: return null
            var off = 0
            for (s in table) {
                val l = s.toInt() and 0xFF
                cur.write(body, off, l)
                off += l
                if (l < 255) {
                    packets++
                    if (packets == 2) {
                        val pkt = cur.toByteArray()
                        val skip = when {
                            pkt.size > 8 && String(pkt, 0, 8, Charsets.ISO_8859_1) == "OpusTags" -> 8
                            pkt.size > 7 && pkt[0] == 3.toByte() && String(pkt, 1, 6, Charsets.ISO_8859_1) == "vorbis" -> 7
                            else -> return null
                        }
                        return vorbisLyrics(pkt, skip)
                    }
                    cur.reset()
                }
            }
            val step = 27L + segs + bodyLen
            pos += step
            total += step
        }
        return null
    }

    // ---------- MP4 / M4A ----------

    /** Ищет атом [type] среди дочерних в диапазоне [start, end); возвращает (начало содержимого, конец). */
    private fun mp4Find(ch: FileChannel, start: Long, end: Long, type: String): Pair<Long, Long>? {
        var pos = start
        while (pos + 8 <= end) {
            val h = ch.readAt(pos, 8) ?: return null
            var sz = beInt(h, 0).toLong() and 0xFFFFFFFFL
            val t = String(h, 4, 4, Charsets.ISO_8859_1)
            var hdr = 8
            if (sz == 1L) {
                val e = ch.readAt(pos + 8, 8) ?: return null
                sz = (beInt(e, 0).toLong() shl 32) or (beInt(e, 4).toLong() and 0xFFFFFFFFL)
                hdr = 16
            } else if (sz == 0L) {
                sz = end - pos
            }
            if (sz < hdr) return null
            val atomEnd = minOf(pos + sz, end)
            if (t == type) return (pos + hdr) to atomEnd
            pos += sz
        }
        return null
    }

    private fun readMp4(ch: FileChannel): TagLyrics? {
        val size = ch.size()
        val moov = mp4Find(ch, 0, size, "moov") ?: return null
        val udta = mp4Find(ch, moov.first, moov.second, "udta") ?: return null
        val meta = mp4Find(ch, udta.first, udta.second, "meta") ?: return null
        // «meta» у iTunes — полный атом: 4 байта версии/флагов перед дочерними
        val first = ch.readAt(meta.first, 4) ?: return null
        val metaStart = if (beInt(first, 0) == 0) meta.first + 4 else meta.first
        val ilst = mp4Find(ch, metaStart, meta.second, "ilst") ?: return null
        val lyr = mp4Find(ch, ilst.first, ilst.second, "\u00A9lyr") ?: return null
        val data = mp4Find(ch, lyr.first, lyr.second, "data") ?: return null
        val len = (data.second - data.first - 8).toInt()
        if (len <= 0 || len > MAX_FRAME) return null
        val b = ch.readAt(data.first + 8, len) ?: return null
        val s = String(b, Charsets.UTF_8)
        return if (s.isBlank()) null else TagLyrics(s, null)
    }
}

/** Загрузка текста песни для трека: .lrc/.txt рядом с файлом и теги. Выбирается лучший вариант: с таймкодами важнее простого. */
object Lyrics {
    private const val MAX_FILE = 2 * 1024 * 1024

    private val cache = object : LinkedHashMap<String, LyricsResult>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LyricsResult>?): Boolean = size > 12
    }

    fun clearCache() {
        synchronized(cache) { cache.clear() }
    }

    suspend fun load(ctx: Context, t: Track): LyricsResult = withContext(Dispatchers.IO) {
        val key = "${t.uri}|${t.lyricsUri}|${t.tagLyrics}"
        synchronized(cache) { cache[key] }?.let { return@withContext it }

        val found = ArrayList<LyricsResult>()
        t.lyricsUri?.let { u ->
            readFile(ctx, Uri.parse(u))?.let { found += LyricsParser.parse(it) }
        }
        if (t.tagLyrics) {
            LyricsTags.read(ctx, Uri.parse(t.uri))?.let { tl ->
                tl.synced?.takeIf { it.isNotEmpty() }?.let { found += LyricsResult.Synced(it) }
                tl.text?.let { found += LyricsParser.parse(it) }
            }
        }
        val best = found.firstOrNull { it is LyricsResult.Synced }
            ?: found.firstOrNull { it !is LyricsResult.None }
            ?: LyricsResult.None
        synchronized(cache) { cache[key] = best }
        best
    }

    private fun readFile(ctx: Context, uri: Uri): String? = runCatching {
        ctx.contentResolver.openInputStream(uri)?.use { s ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (true) {
                val n = s.read(buf)
                if (n <= 0) break
                out.write(buf, 0, n)
                if (out.size() > MAX_FILE) return@use null
            }
            LyricsTags.decodeFile(out.toByteArray())
        }
    }.getOrNull()
}
