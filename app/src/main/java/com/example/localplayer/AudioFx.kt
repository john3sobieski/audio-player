package com.example.localplayer

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tanh

/** Режим эквалайзера. */
enum class EqMode {
    /** Эквалайзер выключен. */
    OFF,

    /** Одна кривая для всех треков. */
    GLOBAL,

    /** Своя кривая у каждого трека (хранится постоянно). */
    PER_TRACK,
}

/**
 * Единое хранилище настроек звука и их «действующих» значений для обработчика.
 *
 * Правила, которые поддерживаются на уровне интерфейса и ViewModel:
 *  - усиление басов можно включить, только если эквалайзер выключен;
 *  - эквалайзер (любой режим) можно включить, только если басы выключены;
 *  - глобальная кривая и кривые отдельных треков хранятся независимо и не стираются при смене режима.
 *
 * Все изменения делаются из главного потока. Обработчик звука (аудиопоток) читает только
 * [gains], [bassDb] и [version], поэтому массивы никогда не изменяются «на месте», а заменяются целиком.
 */
object AudioFx {
    const val BANDS = 12
    const val EQ_RANGE_DB = 12f
    const val BASS_MAX_DB = 15f

    val BAND_FREQS = floatArrayOf(32f, 64f, 125f, 250f, 500f, 1000f, 2000f, 3500f, 6000f, 9000f, 12500f, 16000f)
    val BAND_LABELS = listOf("32", "64", "125", "250", "500", "1к", "2к", "3,5к", "6к", "9к", "12к", "16к")

    /** Добротность каждой полосы подбирается по расстоянию до соседей, чтобы полосы перекрывались ровно. */
    val BAND_Q = DoubleArray(BANDS) { i ->
        val f = BAND_FREQS[i].toDouble()
        val lo = if (i > 0) BAND_FREQS[i - 1].toDouble() else f / 2
        val hi = if (i < BANDS - 1) BAND_FREQS[i + 1].toDouble() else f * 2
        val octaves = ln(hi / lo) / ln(2.0) / 2
        1.0 / (2.0 * sinh(ln(2.0) / 2.0 * octaves))
    }

    private val ZERO = FloatArray(BANDS)

    // ---------- Настройки, которые читает обработчик звука ----------
    @Volatile var mono = false
    @Volatile var normalize = false

    /** Действующая кривая эквалайзера (дБ на полосу). Заменяется целиком. */
    @Volatile var gains: FloatArray = ZERO
        private set

    /** Действующее усиление басов в дБ (0, если выключено или недоступно). */
    @Volatile var bassDb = 0f
        private set

    /** Увеличивается при каждом изменении [gains] / [bassDb], чтобы обработчик пересчитал фильтры. */
    @Volatile var version = 0
        private set

    // ---------- Сохраняемое состояние ----------
    @Volatile var eqMode = EqMode.OFF
    var bassEnabled = false
    var bassLevel = 6f

    var globalGains: FloatArray = ZERO
        private set

    private val trackGains = HashMap<String, FloatArray>()

    @Volatile var currentUri: String? = null
        private set

    private var appCtx: Context? = null
    private var loaded = false
    private val io = Executors.newSingleThreadExecutor()

    // ---------- Загрузка и сохранение ----------

    @Synchronized
    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val app = ctx.applicationContext
        appCtx = app

        val p = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
        mono = p.getBoolean("mono", false)
        normalize = p.getBoolean("normalize", false)
        eqMode = runCatching { EqMode.valueOf(p.getString("eq_mode", "OFF") ?: "OFF") }.getOrDefault(EqMode.OFF)
        bassEnabled = p.getBoolean("bass_enabled", false)
        bassLevel = p.getFloat("bass_db", 6f).coerceIn(0f, BASS_MAX_DB)
        globalGains = parseGains(p.getString("eq_global", null))
        if (bassEnabled && eqMode != EqMode.OFF) bassEnabled = false // на случай повреждённых данных

        try {
            val f = File(app.filesDir, TRACKS_FILE)
            if (f.exists()) {
                val o = JSONObject(f.readText())
                for (k in o.keys()) {
                    val arr = o.getJSONArray(k)
                    if (arr.length() == BANDS) {
                        trackGains[k] = FloatArray(BANDS) { arr.getDouble(it).toFloat().coerceIn(-EQ_RANGE_DB, EQ_RANGE_DB) }
                    }
                }
            }
        } catch (_: Exception) {
        }
        refresh()
    }

    fun persistSettings() {
        appCtx?.getSharedPreferences("settings", Context.MODE_PRIVATE)?.edit()
            ?.putBoolean("mono", mono)
            ?.putBoolean("normalize", normalize)
            ?.putString("eq_mode", eqMode.name)
            ?.putBoolean("bass_enabled", bassEnabled)
            ?.putFloat("bass_db", bassLevel)
            ?.putString("eq_global", globalGains.joinToString(","))
            ?.apply()
    }

    /** Кривые отдельных треков пишутся в отдельный файл во внутренней памяти приложения. */
    fun persistTracks() {
        val dir = appCtx?.filesDir ?: return
        trackGains.entries.removeAll { e -> e.value.all { it == 0f } } // плоские кривые не храним
        val o = JSONObject()
        for ((k, v) in trackGains) {
            val a = JSONArray()
            for (x in v) a.put(x.toDouble())
            o.put(k, a)
        }
        val json = o.toString()
        io.execute {
            try {
                val tmp = File(dir, "$TRACKS_FILE.tmp")
                tmp.writeText(json)
                tmp.renameTo(File(dir, TRACKS_FILE))
            } catch (_: Exception) {
            }
        }
    }

    // ---------- Кривые ----------

    fun setCurrentUri(uri: String?) {
        if (currentUri == uri) return
        currentUri = uri
        refresh()
    }

    fun setGlobalBand(band: Int, db: Float) {
        val c = globalGains.copyOf()
        c[band] = db
        globalGains = c
        refresh()
    }

    fun resetGlobal() {
        globalGains = ZERO
        refresh()
    }

    fun trackGainsFor(uri: String?): FloatArray = uri?.let { trackGains[key(it)] } ?: ZERO

    fun setTrackBand(uri: String, band: Int, db: Float) {
        val k = key(uri)
        val c = (trackGains[k] ?: ZERO).copyOf()
        c[band] = db
        trackGains[k] = c
        refresh()
    }

    fun resetTrack(uri: String) {
        trackGains.remove(key(uri))
        refresh()
    }

    /** Сколько треков имеют собственную (не плоскую) кривую. */
    fun customTrackCount(): Int = trackGains.values.count { v -> v.any { it != 0f } }

    /** Пересчитывает действующие значения по режиму и текущему треку. */
    fun refresh() {
        gains = when (eqMode) {
            EqMode.OFF -> ZERO
            EqMode.GLOBAL -> globalGains
            EqMode.PER_TRACK -> currentUri?.let { trackGains[key(it)] } ?: ZERO
        }
        bassDb = if (bassEnabled && eqMode == EqMode.OFF) bassLevel else 0f
        version++
    }

    /**
     * Ключ трека. Берём идентификатор документа (например, «primary:Music/Rock/song.mp3»), а не весь адрес:
     * он не зависит от того, какую папку выбрали в настройках, поэтому настройки трека переживают смену папки.
     */
    private fun key(uri: String): String = try {
        DocumentsContract.getDocumentId(Uri.parse(uri))
    } catch (_: Exception) {
        uri
    }

    private fun parseGains(s: String?): FloatArray {
        val parts = s?.split(",") ?: return ZERO
        if (parts.size != BANDS) return ZERO
        return try {
            FloatArray(BANDS) { parts[it].toFloat().coerceIn(-EQ_RANGE_DB, EQ_RANGE_DB) }
        } catch (_: Exception) {
            ZERO
        }
    }

    private const val TRACKS_FILE = "eq_tracks.json"
}

/**
 * Обработчик звука для ExoPlayer. Порядок обработки:
 *  1. Моно (усреднение левого и правого каналов).
 *  2. Нормализация громкости (медленная автоподстройка усиления к целевому уровню).
 *  3. 12-полосный эквалайзер (пиковые биквад-фильтры) и усиление басов (низкочастотная «полка»).
 *  4. Мягкий ограничитель пиков.
 *
 * Работает с 16-битным PCM и float PCM; для остальных форматов остаётся неактивным.
 */
@UnstableApi
class AudioFxProcessor : BaseAudioProcessor() {

    private var floatMode = false
    private var channels = 2
    private var sampleRate = 44100.0

    // Нормализация
    private var env = TARGET_POWER
    private var gain = 1.0
    private var envAttack = 0.0
    private var envRelease = 0.0
    private var gainAttack = 0.0
    private var gainRelease = 0.0

    // Фильтры: BANDS полос эквалайзера + 1 фильтр басов
    private val filterCount = AudioFx.BANDS + 1
    private val b0 = DoubleArray(filterCount)
    private val b1 = DoubleArray(filterCount)
    private val b2 = DoubleArray(filterCount)
    private val a1 = DoubleArray(filterCount)
    private val a2 = DoubleArray(filterCount)
    private var active = IntArray(0)
    private var z = DoubleArray(0) // состояния фильтров: канал × фильтр × 2
    private var appliedVersion = -1
    private var preamp = 1.0

    private var frame = DoubleArray(2)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val enc = inputAudioFormat.encoding
        if ((enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) || inputAudioFormat.channelCount < 1) {
            return AudioProcessor.AudioFormat.NOT_SET // обработчик неактивен для этого формата
        }
        floatMode = enc == C.ENCODING_PCM_FLOAT
        channels = inputAudioFormat.channelCount
        frame = DoubleArray(channels)
        z = DoubleArray(channels * filterCount * 2)
        sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8000).toDouble()
        appliedVersion = -1

        envAttack = coef(0.25)
        envRelease = coef(0.8)
        gainAttack = coef(0.3)
        gainRelease = coef(2.0)
        return inputAudioFormat
    }

    override fun onFlush() {
        env = TARGET_POWER
        gain = 1.0
        z.fill(0.0)
    }

    override fun onReset() {
        onFlush()
        floatMode = false
        channels = 2
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size)

        if (appliedVersion != AudioFx.version) rebuildFilters()

        val doMono = AudioFx.mono && channels == 2
        val doNorm = AudioFx.normalize
        val hasFilters = active.isNotEmpty()
        val doLimit = doNorm || hasFilters

        if (!doMono && !doNorm && !hasFilters) {
            gain = 1.0
            env = TARGET_POWER
            out.put(inputBuffer)
            out.flip()
            return
        }

        val bytesPerSample = if (floatMode) 4 else 2
        val frames = size / (channels * bytesPerSample)
        val s = frame
        val stride = filterCount * 2

        for (n in 0 until frames) {
            for (c in 0 until channels) {
                s[c] = if (floatMode) inputBuffer.getFloat().toDouble() else inputBuffer.getShort() / 32768.0
            }
            if (doMono) {
                val m = (s[0] + s[1]) * 0.5
                s[0] = m
                s[1] = m
            }
            if (doNorm) normalizeFrame(s)
            if (hasFilters) {
                for (c in 0 until channels) {
                    var x = s[c] * preamp
                    val base = c * stride
                    for (f in active) {
                        val zi = base + f * 2
                        val y = b0[f] * x + z[zi]
                        z[zi] = b1[f] * x - a1[f] * y + z[zi + 1]
                        z[zi + 1] = b2[f] * x - a2[f] * y
                        x = y
                    }
                    s[c] = x
                }
            }
            for (c in 0 until channels) {
                val v = if (doLimit) softLimit(s[c]) else s[c]
                if (floatMode) {
                    out.putFloat(v.toFloat())
                } else {
                    out.putShort((v.coerceIn(-1.0, 32767.0 / 32768.0) * 32768.0).toInt().toShort())
                }
            }
        }
        // неполный кадр в конце буфера (на практике не встречается) просто копируем
        while (inputBuffer.hasRemaining()) out.put(inputBuffer.get())
        out.flip()
    }

    // ---------- Нормализация ----------

    private fun normalizeFrame(s: DoubleArray) {
        var p = 0.0
        for (v in s) p += v * v
        p /= s.size

        env += (p - env) * (if (p > env) envAttack else envRelease)

        val desired = if (env < GATE_POWER) {
            gain // тишина: усиление не меняем
        } else {
            (TARGET_RMS / sqrt(env)).coerceIn(MIN_GAIN, MAX_GAIN)
        }
        gain += (desired - gain) * (if (desired < gain) gainAttack else gainRelease)

        for (c in s.indices) s[c] *= gain
    }

    // ---------- Фильтры ----------

    private fun rebuildFilters() {
        appliedVersion = AudioFx.version
        val gains = AudioFx.gains
        val bass = AudioFx.bassDb.toDouble()

        val on = BooleanArray(filterCount)
        var maxBoost = 0.0
        for (i in 0 until AudioFx.BANDS) {
            val g = gains[i].toDouble()
            val f = AudioFx.BAND_FREQS[i].toDouble()
            if (abs(g) < 0.05 || f >= sampleRate * 0.45) continue
            peaking(i, f, g, AudioFx.BAND_Q[i])
            on[i] = true
            maxBoost = max(maxBoost, g)
        }
        if (bass > 0.05) {
            lowShelf(AudioFx.BANDS, BASS_CORNER_HZ, bass)
            on[AudioFx.BANDS] = true
            maxBoost = max(maxBoost, bass)
        }

        // Выключенным фильтрам обнуляем состояние, чтобы при повторном включении не было щелчка
        val stride = filterCount * 2
        for (f in 0 until filterCount) {
            if (!on[f]) for (c in 0 until channels) {
                val zi = c * stride + f * 2
                if (zi + 1 < z.size) { z[zi] = 0.0; z[zi + 1] = 0.0 }
            }
        }
        active = (0 until filterCount).filter { on[it] }.toIntArray()

        // Запас по уровню: часть усиления компенсируем, чтобы реже упираться в ограничитель
        preamp = 10.0.pow(-0.5 * maxBoost / 20.0)
    }

    /** Пиковый (колокольный) фильтр, формулы RBJ Audio EQ Cookbook. */
    private fun peaking(i: Int, freq: Double, gainDb: Double, q: Double) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freq / sampleRate
        val alpha = sin(w0) / (2.0 * q)
        val cosW = cos(w0)
        val a0 = 1.0 + alpha / a
        b0[i] = (1.0 + alpha * a) / a0
        b1[i] = (-2.0 * cosW) / a0
        b2[i] = (1.0 - alpha * a) / a0
        a1[i] = (-2.0 * cosW) / a0
        a2[i] = (1.0 - alpha / a) / a0
    }

    /** Низкочастотная «полка» (усиление басов), формулы RBJ Audio EQ Cookbook при S = 1. */
    private fun lowShelf(i: Int, freq: Double, gainDb: Double) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freq / sampleRate
        val cosW = cos(w0)
        val alpha = sin(w0) / 2.0 * sqrt(2.0)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
        val a0 = (a + 1) + (a - 1) * cosW + twoSqrtAAlpha
        b0[i] = a * ((a + 1) - (a - 1) * cosW + twoSqrtAAlpha) / a0
        b1[i] = 2.0 * a * ((a - 1) - (a + 1) * cosW) / a0
        b2[i] = a * ((a + 1) - (a - 1) * cosW - twoSqrtAAlpha) / a0
        a1[i] = -2.0 * ((a - 1) + (a + 1) * cosW) / a0
        a2[i] = ((a + 1) + (a - 1) * cosW - twoSqrtAAlpha) / a0
    }

    private fun softLimit(x: Double): Double {
        val a = abs(x)
        return if (a <= LIMIT_KNEE) x else sign(x) * (LIMIT_KNEE + (1.0 - LIMIT_KNEE) * tanh((a - LIMIT_KNEE) / (1.0 - LIMIT_KNEE)))
    }

    private fun coef(seconds: Double) = 1.0 - exp(-1.0 / (sampleRate * seconds))

    private companion object {
        const val TARGET_RMS = 0.16           // около -16 dBFS
        const val TARGET_POWER = TARGET_RMS * TARGET_RMS
        const val GATE_POWER = 1e-6           // около -60 dBFS: ниже считаем тишиной
        const val MIN_GAIN = 0.25             // -12 dB
        const val MAX_GAIN = 2.8              // +9 dB
        const val LIMIT_KNEE = 0.9
        const val BASS_CORNER_HZ = 110.0
    }
}
