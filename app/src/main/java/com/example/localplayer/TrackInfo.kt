package com.example.localplayer

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Данные для окна «О треке»: теги и свойства файла. Путь к папке намеренно не показывается. */
object TrackDetails {

    data class Section(val title: String, val rows: List<Pair<String, String>>)

    suspend fun load(ctx: Context, t: Track, channels: Int): List<Section> = withContext(Dispatchers.IO) {
        val uri = Uri.parse(t.uri)
        val r = MediaMetadataRetriever()
        val tags = ArrayList<Pair<String, String>>()
        val file = ArrayList<Pair<String, String>>()
        var mime: String? = null
        var coverSize: String? = null
        var rateFallback = 0
        var bitsFallback = 0
        try {
            runCatching { r.setDataSource(ctx, uri) }
            fun m(k: Int): String? = runCatching { r.extractMetadata(k)?.trim()?.takeIf { it.isNotEmpty() } }.getOrNull()

            tags += "Название" to t.title
            if (t.artist != NO_ARTIST) tags += "Исполнитель" to t.artist
            if (t.albumArtist != NO_ARTIST && t.albumArtist != t.artist) tags += "Исполнитель альбома" to t.albumArtist
            if (t.album != NO_ALBUM) tags += "Альбом" to t.album
            if (t.trackNo > 0) {
                val total = m(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS)?.toIntOrNull()
                tags += "Номер трека" to if (total != null && total > 0) "${t.trackNo} из $total" else "${t.trackNo}"
            }
            m(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)?.let { tags += "Диск" to it }
            m(MediaMetadataRetriever.METADATA_KEY_GENRE)?.let { tags += "Жанр" to it }
            (m(MediaMetadataRetriever.METADATA_KEY_YEAR) ?: m(MediaMetadataRetriever.METADATA_KEY_DATE))
                ?.let { tags += "Год" to it }
            m(MediaMetadataRetriever.METADATA_KEY_COMPOSER)?.let { tags += "Композитор" to it }
            m(MediaMetadataRetriever.METADATA_KEY_WRITER)?.let { tags += "Автор текста" to it }
            val author = m(MediaMetadataRetriever.METADATA_KEY_AUTHOR)
            if (author != null && author != t.artist && author != m(MediaMetadataRetriever.METADATA_KEY_WRITER)) {
                tags += "Автор" to author
            }
            m(MediaMetadataRetriever.METADATA_KEY_COMPILATION)?.let { tags += "Сборник" to it }

            mime = m(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            if (Build.VERSION.SDK_INT >= 31) {
                rateFallback = m(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0
                bitsFallback = m(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull() ?: 0
            }
            val pic = runCatching { r.embeddedPicture }.getOrNull()
            coverSize = if (pic == null) "нет" else {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(pic, 0, pic.size, o)
                if (o.outWidth > 0) "${o.outWidth}×${o.outHeight}" else "есть"
            }
        } finally {
            runCatching { r.release() }
        }

        var size = 0L
        var name: String? = null
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        name = c.getString(0)
                        size = c.getLong(1)
                    }
                }
        }

        val info = t.info
        if (info.format.isNotEmpty()) file += "Формат" to info.format
        if (info.bitrateKbps > 0) {
            file += "Битрейт" to "${info.bitrateKbps} кбит/с"
        } else if (info.lossless && size > 0 && t.durationMs > 0) {
            file += "Средний битрейт" to "≈ ${size * 8 / t.durationMs} кбит/с"
        }
        (info.sampleRateHz.takeIf { it > 0 } ?: rateFallback.takeIf { it > 0 })?.let { file += "Частота дискретизации" to khz(it) }
        (info.bitsPerSample.takeIf { it > 0 } ?: bitsFallback.takeIf { it > 0 })?.let { file += "Разрядность" to "$it бит" }
        if (channels > 0) {
            file += "Каналы" to when (channels) {
                1 -> "Моно"
                2 -> "Стерео"
                else -> "$channels"
            }
        }
        if (t.durationMs > 0) file += "Длительность" to duration(t.durationMs)
        mime?.let { file += "MIME-тип" to it }
        if (size > 0) file += "Размер файла" to fileSize(size)
        name?.takeIf { it.isNotBlank() }?.let { file += "Имя файла" to it }
        coverSize?.let { file += "Обложка" to it }
        if (t.modified > 0) file += "Дата файла" to date(t.modified)
        if (t.added > 0) file += "Добавлен в плеер" to date(t.added)

        listOf(Section("Теги", tags), Section("Файл", file)).filter { it.rows.isNotEmpty() }
    }

    private fun khz(hz: Int): String =
        if (hz % 1000 == 0) "${hz / 1000} кГц" else String.format(Locale("ru"), "%.1f кГц", hz / 1000.0)

    private fun duration(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%d:%02d", m, sec)
    }

    private fun fileSize(bytes: Long): String {
        val mb = bytes / 1024.0 / 1024.0
        return if (mb >= 1) String.format(Locale("ru"), "%.2f МБ", mb) else String.format(Locale("ru"), "%.0f КБ", bytes / 1024.0)
    }

    private fun date(ms: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(ms))
}
