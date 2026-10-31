package com.example.localplayer

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.Color
import java.io.InputStream
import androidx.annotation.StringRes
import java.util.Locale
import kotlin.math.abs

/**
 * Технические параметры файла, которые показывает плашка качества в плеере.
 * Для lossless (FLAC/WAV) важны разрядность и частота дискретизации, для остальных форматов — битрейт.
 */
data class AudioInfo(
    val format: String = "",
    val bitrateKbps: Int = 0,
    val sampleRateHz: Int = 0,
    val bitsPerSample: Int = 0,
    val lossless: Boolean = false,
)

/** Текст плашки: «MP3 / 192 kbps» или «FLAC / 24 Bit – 96 kHz». */
fun AudioInfo.label(): String {
    if (format.isEmpty()) return ""
    val details = if (lossless) {
        listOfNotNull(
            bitsPerSample.takeIf { it > 0 }?.let { "$it Bit" },
            sampleRateHz.takeIf { it > 0 }?.let { formatKhz(it) },
        ).joinToString(" – ")
    } else if (bitrateKbps > 0) {
        "$bitrateKbps kbps"
    } else {
        ""
    }
    return if (details.isEmpty()) format else "$format / $details"
}

private fun formatKhz(hz: Int): String =
    if (hz % 1000 == 0) "${hz / 1000} kHz" else String.format(Locale.US, "%.1f kHz", hz / 1000.0)

/**
 * «Редкость» качества, как в играх: чем лучше звук, тем реже и ярче цвет.
 * Градиент рисуется слева направо от [from] к [to], [fg] — цвет текста на плашке.
 */
enum class QualityTier(@StringRes val title: Int, val from: Color, val to: Color, val fg: Color) {
    COMMON(R.string.tier_common, Color(0xFF9BDCFB), Color(0xFF4FC3F7), Color(0xFF04283A)),          // светло-голубой
    UNCOMMON(R.string.tier_uncommon, Color(0xFFA5E08A), Color(0xFF4CAF50), Color(0xFF0B2A0D)),      // зелёный
    RARE(R.string.tier_rare, Color(0xFF4C8DFF), Color(0xFF2962FF), Color.White),                   // синий
    EPIC(R.string.tier_epic, Color(0xFFB05CFF), Color(0xFF7B1FA2), Color.White),                // фиолетовый
    MYTHIC(R.string.tier_mythic, Color(0xFFFF5252), Color(0xFFC62828), Color.White),             // красный
    LEGENDARY(R.string.tier_legendary, Color(0xFFFFE082), Color(0xFFFFA000), Color(0xFF3A2400)),   // золотой
}

/**
 * Пороги редкости (меняются в одном месте):
 *  - с потерями: до 192 kbps — обычное, до 256 — необычное, выше — редкое (MP3 320 и т.п.)
 *  - lossless 16 Bit и до 48 kHz (CD-качество) — эпическое
 *  - hi-res (глубже 16 Bit или выше 48 kHz) — мифическое
 *  - 24 Bit и от 176.4 kHz — легендарное
 */
fun AudioInfo.tier(): QualityTier = if (lossless) {
    when {
        bitsPerSample >= 24 && sampleRateHz >= 176_400 -> QualityTier.LEGENDARY
        bitsPerSample > 16 || sampleRateHz > 48_000 -> QualityTier.MYTHIC
        else -> QualityTier.EPIC
    }
} else {
    when {
        bitrateKbps <= 192 -> QualityTier.COMMON
        bitrateKbps <= 256 -> QualityTier.UNCOMMON
        else -> QualityTier.RARE
    }
}

/** Определяет формат и качество файла. Вызывается при сканировании, результат кэшируется вместе с треком. */
object AudioProbe {

    private val STANDARD_KBPS = intArrayOf(32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)

    fun probe(
        ctx: Context,
        uri: Uri,
        fileName: String,
        fileSize: Long,
        durationMs: Long,
        retriever: MediaMetadataRetriever?,
    ): AudioInfo {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val format = when (ext) {
            "mp3" -> "MP3"
            "flac" -> "FLAC"
            "wav" -> "WAV"
            "ogg", "oga" -> "OGG"
            "opus" -> "OPUS"
            "m4a", "aac" -> "AAC"
            "wma" -> "WMA"
            "mka" -> "MKA"
            "amr" -> "AMR"
            else -> ext.uppercase()
        }
        if (format.isEmpty()) return AudioInfo()

        fun meta(key: Int): Int =
            runCatching { retriever?.extractMetadata(key)?.toIntOrNull() }.getOrNull() ?: 0

        val lossless = ext == "flac" || ext == "wav"
        if (lossless) {
            // Точные значения берём прямо из заголовка файла: так работает на любой версии Android
            val parsed = runCatching {
                ctx.contentResolver.openInputStream(uri)?.use { if (ext == "flac") readFlac(it) else readWav(it) }
            }.getOrNull()
            var rate = parsed?.first ?: 0
            var bits = parsed?.second ?: 0
            if (Build.VERSION.SDK_INT >= 31) {
                if (rate == 0) rate = meta(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                if (bits == 0) bits = meta(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)
            }
            return AudioInfo(format, 0, rate, bits, true)
        }

        var kbps = meta(MediaMetadataRetriever.METADATA_KEY_BITRATE) / 1000
        if (kbps <= 0 && fileSize > 0 && durationMs > 0) kbps = (fileSize * 8 / durationMs).toInt()
        return AudioInfo(format, snap(kbps), 0, 0, false)
    }

    /** 191 или 193 kbps у CBR-файла — это на самом деле 192: «прилипаем» к стандартным значениям с допуском 2%. */
    private fun snap(kbps: Int): Int {
        val nearest = STANDARD_KBPS.minByOrNull { abs(it - kbps) } ?: return kbps
        return if (abs(nearest - kbps) * 100 <= nearest * 2) nearest else kbps
    }

    // ---------- Чтение заголовков ----------

    private fun InputStream.readFully(buf: ByteArray): Boolean {
        var off = 0
        while (off < buf.size) {
            val r = read(buf, off, buf.size - off)
            if (r < 0) return false
            off += r
        }
        return true
    }

    private fun InputStream.skipFully(count: Long) {
        var left = count
        while (left > 0) {
            val s = skip(left)
            if (s > 0) {
                left -= s
            } else {
                if (read() < 0) return
                left--
            }
        }
    }

    /** FLAC: блок STREAMINFO. Возвращает (частота, разрядность). Перед «fLaC» может стоять тег ID3v2. */
    private fun readFlac(ins: InputStream): Pair<Int, Int>? {
        val marker = ByteArray(4)
        if (!ins.readFully(marker)) return null
        if (marker[0] == 'I'.code.toByte() && marker[1] == 'D'.code.toByte() && marker[2] == '3'.code.toByte()) {
            val rest = ByteArray(6) // версия (ревизия), флаги, размер (4 байта, по 7 бит)
            if (!ins.readFully(rest)) return null
            val size = ((rest[2].toInt() and 0x7F) shl 21) or ((rest[3].toInt() and 0x7F) shl 14) or
                ((rest[4].toInt() and 0x7F) shl 7) or (rest[5].toInt() and 0x7F)
            ins.skipFully(size.toLong())
            if (!ins.readFully(marker)) return null
        }
        if (String(marker, Charsets.ISO_8859_1) != "fLaC") return null
        val blockHeader = ByteArray(4)
        if (!ins.readFully(blockHeader)) return null
        if ((blockHeader[0].toInt() and 0x7F) != 0) return null // первым всегда идёт STREAMINFO (тип 0)
        val info = ByteArray(18)
        if (!ins.readFully(info)) return null
        fun u(i: Int) = info[i].toInt() and 0xFF
        val rate = (u(10) shl 12) or (u(11) shl 4) or (u(12) ushr 4)
        val bits = (((u(12) and 0x01) shl 4) or (u(13) ushr 4)) + 1
        return rate to bits
    }

    /** WAV: блок «fmt ». Возвращает (частота, разрядность). */
    private fun readWav(ins: InputStream): Pair<Int, Int>? {
        val head = ByteArray(12)
        if (!ins.readFully(head)) return null
        val tag = String(head, 0, 4, Charsets.ISO_8859_1)
        if (tag != "RIFF" && tag != "RF64" && tag != "BW64") return null
        repeat(32) {
            val ch = ByteArray(8)
            if (!ins.readFully(ch)) return null
            val id = String(ch, 0, 4, Charsets.ISO_8859_1)
            val size = (ch[4].toLong() and 0xFF) or ((ch[5].toLong() and 0xFF) shl 8) or
                ((ch[6].toLong() and 0xFF) shl 16) or ((ch[7].toLong() and 0xFF) shl 24)
            if (id == "fmt ") {
                if (size < 16) return null
                val f = ByteArray(16)
                if (!ins.readFully(f)) return null
                fun u(i: Int) = f[i].toInt() and 0xFF
                val rate = u(4) or (u(5) shl 8) or (u(6) shl 16) or (u(7) shl 24)
                val bits = u(14) or (u(15) shl 8)
                return rate to bits
            }
            ins.skipFully(size + (size and 1L)) // блоки выровнены по 2 байта
        }
        return null
    }
}
