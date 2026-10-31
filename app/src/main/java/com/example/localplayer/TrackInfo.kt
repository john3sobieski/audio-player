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
        // Подписи берутся из ресурсов контекста, поэтому уже на выбранном языке; числа форматируются по языку интерфейса
        val ui = Locale.forLanguageTag(ctx.getString(R.string.ui_lang_tag))
        fun s(id: Int, vararg args: Any): String = ctx.getString(id, *args)
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

            tags += s(R.string.ti_title) to t.title
            if (t.artist != NO_ARTIST) tags += s(R.string.ti_artist) to t.artist
            if (t.albumArtist != NO_ARTIST && t.albumArtist != t.artist) tags += s(R.string.ti_album_artist) to t.albumArtist
            if (t.album != NO_ALBUM) tags += s(R.string.ti_album) to t.album
            if (t.trackNo > 0) {
                val total = m(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS)?.toIntOrNull()
                tags += s(R.string.ti_track_no) to if (total != null && total > 0) s(R.string.ti_track_of, t.trackNo, total) else "${t.trackNo}"
            }
            m(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)?.let { tags += s(R.string.ti_disc) to it }
            m(MediaMetadataRetriever.METADATA_KEY_GENRE)?.let { tags += s(R.string.ti_genre) to it }
            (m(MediaMetadataRetriever.METADATA_KEY_YEAR) ?: m(MediaMetadataRetriever.METADATA_KEY_DATE))
                ?.let { tags += s(R.string.ti_year) to it }
            m(MediaMetadataRetriever.METADATA_KEY_COMPOSER)?.let { tags += s(R.string.ti_composer) to it }
            m(MediaMetadataRetriever.METADATA_KEY_WRITER)?.let { tags += s(R.string.ti_lyricist) to it }
            val author = m(MediaMetadataRetriever.METADATA_KEY_AUTHOR)
            if (author != null && author != t.artist && author != m(MediaMetadataRetriever.METADATA_KEY_WRITER)) {
                tags += s(R.string.ti_author) to author
            }
            m(MediaMetadataRetriever.METADATA_KEY_COMPILATION)?.let { tags += s(R.string.ti_compilation) to it }

            mime = m(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            if (Build.VERSION.SDK_INT >= 31) {
                rateFallback = m(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0
                bitsFallback = m(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull() ?: 0
            }
            val pic = runCatching { r.embeddedPicture }.getOrNull()
            coverSize = if (pic == null) s(R.string.ti_none) else {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(pic, 0, pic.size, o)
                if (o.outWidth > 0) "${o.outWidth}×${o.outHeight}" else s(R.string.ti_present)
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
        if (info.format.isNotEmpty()) file += s(R.string.ti_format) to info.format
        if (info.bitrateKbps > 0) {
            file += s(R.string.ti_bitrate) to s(R.string.fmt_kbps, "${info.bitrateKbps}")
        } else if (info.lossless && size > 0 && t.durationMs > 0) {
            file += s(R.string.ti_avg_bitrate) to "≈ " + s(R.string.fmt_kbps, "${size * 8 / t.durationMs}")
        }
        (info.sampleRateHz.takeIf { it > 0 } ?: rateFallback.takeIf { it > 0 })?.let { file += s(R.string.ti_sample_rate) to khz(ctx, ui, it) }
        (info.bitsPerSample.takeIf { it > 0 } ?: bitsFallback.takeIf { it > 0 })?.let { file += s(R.string.ti_bit_depth) to s(R.string.fmt_bits, it) }
        if (channels > 0) {
            file += s(R.string.ti_channels) to when (channels) {
                1 -> s(R.string.mono)
                2 -> s(R.string.ti_stereo)
                else -> "$channels"
            }
        }
        if (t.durationMs > 0) file += s(R.string.ti_duration) to duration(t.durationMs)
        mime?.let { file += s(R.string.ti_mime) to it }
        if (size > 0) file += s(R.string.ti_file_size) to fileSize(ctx, ui, size)
        name?.takeIf { it.isNotBlank() }?.let { file += s(R.string.ti_file_name) to it }
        coverSize?.let { file += s(R.string.ti_cover) to it }
        if (t.modified > 0) file += s(R.string.ti_file_date) to date(ui, t.modified)
        if (t.added > 0) file += s(R.string.ti_added) to date(ui, t.added)

        listOf(Section(s(R.string.ti_section_tags), tags), Section(s(R.string.ti_section_file), file)).filter { it.rows.isNotEmpty() }
    }

    private fun khz(ctx: Context, ui: Locale, hz: Int): String =
        ctx.getString(R.string.fmt_khz, if (hz % 1000 == 0) "${hz / 1000}" else String.format(ui, "%.1f", hz / 1000.0))

    private fun duration(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%d:%02d", m, sec)
    }

    private fun fileSize(ctx: Context, ui: Locale, bytes: Long): String {
        val mb = bytes / 1024.0 / 1024.0
        return if (mb >= 1) ctx.getString(R.string.fmt_mb, String.format(ui, "%.2f", mb))
        else ctx.getString(R.string.fmt_kb, String.format(ui, "%.0f", bytes / 1024.0))
    }

    private fun date(ui: Locale, ms: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm", ui).format(Date(ms))
}
