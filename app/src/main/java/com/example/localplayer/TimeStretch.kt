package com.example.localplayer

import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.DefaultAudioSink
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Цепочка обработки звука для DefaultAudioSink вместо стандартной: сначала наши эффекты (эквалайзер и т. д.),
 * затем [TimeStretchProcessor] вместо встроенного Sonic. Sonic рассчитан на речь и на музыке при замедлении
 * «рвёт» звук (повторяет куски по одному периоду основного тона); наш растяжитель устроен иначе, см. ниже.
 */
@UnstableApi
class SpeedAudioProcessorChain(vararg effects: AudioProcessor) : DefaultAudioSink.AudioProcessorChain {

    private val stretch = TimeStretchProcessor()
    private val processors: Array<AudioProcessor> = arrayOf<AudioProcessor>(*effects, stretch)

    override fun getAudioProcessors(): Array<AudioProcessor> = processors

    override fun applyPlaybackParameters(playbackParameters: PlaybackParameters): PlaybackParameters {
        stretch.speed = playbackParameters.speed
        // Высоту тона не меняем: меняется только скорость
        return PlaybackParameters(stretch.speed, 1f)
    }

    override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean): Boolean = false

    override fun getMediaDuration(playoutDuration: Long): Long =
        (playoutDuration * stretch.speed.toDouble()).toLong()

    override fun getSkippedOutputFrameCount(): Long = 0L
}

/**
 * Изменение скорости без изменения высоты тона: фазовый вокодер с «жёсткой» привязкой фаз (identity phase locking).
 *
 * Почему так звучит лучше Sonic при замедлении:
 *  - звук не режется на куски и не повторяется: каждый кадр спектра (окно ~90 мс) плавно «доигрывается» дольше,
 *    а фазы всех гармоник продолжаются непрерывно, поэтому нет щелчков и «дребезга» на стыках;
 *  - фазы внутри одного пика спектра (гармоники и её «хвост») сдвигаются одинаково, это убирает «металлическую»
 *    окраску, характерную для простого вокодера;
 *  - у стереопары фазовый сдвиг считается по сумме каналов и применяется к обоим одинаково, поэтому
 *    стереокартина (панорама, разница фаз между каналами) не плывёт.
 *
 * Нагрузка небольшая: на пару каналов приходится один прямой и один обратный БПФ на кадр (около 43 кадров в секунду
 * при 44,1 кГц), несколько процентов одного ядра. При скорости 1x до первого изменения скорости в треке
 * процессор просто пропускает звук как есть и ничего не считает.
 */
@UnstableApi
class TimeStretchProcessor : BaseAudioProcessor() {

    /** Скорость: больше 1 — быстрее, меньше 1 — медленнее. */
    @Volatile
    var speed: Float = 1f
        set(value) {
            field = value.coerceIn(0.25f, 4f)
        }

    private var floatMode = false
    private var channels = 2
    private var sampleRate = 44100
    private var engine: PhaseVocoder? = null

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val enc = inputAudioFormat.encoding
        if ((enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) || inputAudioFormat.channelCount < 1) {
            return AudioProcessor.AudioFormat.NOT_SET // для этого формата не работаем
        }
        floatMode = enc == C.ENCODING_PCM_FLOAT
        channels = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8000)
        return inputAudioFormat
    }

    override fun onFlush() {
        engine = null
    }

    override fun onReset() {
        engine = null
        floatMode = false
        channels = 2
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        val frameSize = channels * if (floatMode) 4 else 2

        var eng = engine
        if (eng == null) {
            if (abs(speed - 1f) < 0.001f) {
                // Обычная скорость: звук идёт напрямую, без задержки и нагрузки
                val out = replaceOutputBuffer(bytes)
                out.put(inputBuffer)
                out.flip()
                return
            }
            // Растяжитель включается «на лету» с текущего места: предыдущее уже отыграно напрямую, дыры нет
            eng = PhaseVocoder(channels, sampleRate)
            engine = eng
        }

        val frames = bytes / frameSize
        eng.pushInput(inputBuffer, frames, floatMode)
        inputBuffer.position(inputBuffer.limit()) // неполный кадр в конце (на практике не бывает) отбрасываем
        eng.process(speed, finish = false)
        writeOutput(eng, frameSize)
    }

    override fun onQueueEndOfStream() {
        val eng = engine ?: return
        eng.process(speed, finish = true)
        writeOutput(eng, channels * if (floatMode) 4 else 2)
    }

    private fun writeOutput(eng: PhaseVocoder, frameSize: Int) {
        val frames = eng.outFrames
        if (frames <= 0) return
        val out = replaceOutputBuffer(frames * frameSize)
        eng.drainTo(out, floatMode)
        out.flip()
    }
}

/**
 * Потоковый фазовый вокодер. Окно Ханна ~90 мс (4096 отсчётов при 44,1 кГц), шаг синтеза 1/4 окна.
 * Входные кадры берутся с шагом (шаг синтеза × скорость), выходные кадры всегда ставятся с постоянным шагом.
 */
internal class PhaseVocoder(private val channels: Int, sampleRate: Int) {

    private val n: Int = run {
        val log2 = ln(sampleRate * 0.09) / ln(2.0)
        (1 shl log2.roundToInt()).coerceIn(2048, 16384)
    }
    private val half = n / 2
    private val hs = n / 4

    private val window = DoubleArray(n) { 0.5 - 0.5 * cos(2.0 * PI * it / n) }
    private val window2 = DoubleArray(n) { window[it] * window[it] }

    // Таблицы БПФ
    private val cosT = DoubleArray(half) { cos(2.0 * PI * it / n) }
    private val sinT = DoubleArray(half) { sin(2.0 * PI * it / n) }
    private val rev = IntArray(n).also { r ->
        val bits = Integer.numberOfTrailingZeros(n)
        for (i in 0 until n) r[i] = Integer.reverse(i) ushr (32 - bits)
    }

    // Вход: очередь отсчётов по каналам; inBase — номер отсчёта (с начала работы), лежащего в inBuf[..][0]
    private var inBuf = Array(channels) { FloatArray(n * 4) }
    private var inBase = 0L
    private var inLen = 0
    private var totalIn = 0L
    private var anaPos = 0.0
    private var prevStart = -1L
    private var finished = false

    // Накопитель перекрытия (overlap-add) и сумма квадратов окон для нормировки
    private val acc = Array(channels) { DoubleArray(n) }
    private val nrm = DoubleArray(n)

    // Готовый выход
    private var outCh = Array(channels) { FloatArray(n * 4) }
    var outFrames = 0
        private set

    private val groups: Array<Group> = Array((channels + 1) / 2) { g ->
        val a = g * 2
        Group(a, if (a + 1 < channels) a + 1 else -1)
    }

    // ------------------------------------------------------------------
    // Ввод / вывод
    // ------------------------------------------------------------------

    fun pushInput(buf: ByteBuffer, frames: Int, floatMode: Boolean) {
        ensureIn(inLen + frames)
        for (f in 0 until frames) {
            for (c in 0 until channels) {
                inBuf[c][inLen + f] = if (floatMode) buf.getFloat() else buf.getShort() / 32768f
            }
        }
        inLen += frames
        totalIn += frames
    }

    /** Переносит готовый звук в выходной буфер и очищает свою выходную очередь. */
    fun drainTo(out: ByteBuffer, floatMode: Boolean) {
        for (f in 0 until outFrames) {
            for (c in 0 until channels) {
                val v = outCh[c][f]
                if (floatMode) {
                    out.putFloat(v)
                } else {
                    out.putShort(Math.round(v * 32768f).coerceIn(-32768, 32767).toShort())
                }
            }
        }
        outFrames = 0
    }

    private fun ensureIn(need: Int) {
        if (inBuf[0].size >= need) return
        val size = max(need, inBuf[0].size * 2)
        for (c in 0 until channels) inBuf[c] = inBuf[c].copyOf(size)
    }

    private fun ensureOut(need: Int) {
        if (outCh[0].size >= need) return
        val size = max(need, outCh[0].size * 2)
        for (c in 0 until channels) outCh[c] = outCh[c].copyOf(size)
    }

    // ------------------------------------------------------------------
    // Основной цикл
    // ------------------------------------------------------------------

    /**
     * Обрабатывает все кадры, для которых уже хватает входных данных. При [finish] = true дописывает тишину
     * в конец входа, доигрывает «хвост» и отдаёт всё, что накопилось.
     */
    fun process(speed: Float, finish: Boolean) {
        if (finish) {
            if (finished) return
            finished = true
            ensureIn(inLen + n)
            for (c in 0 until channels) java.util.Arrays.fill(inBuf[c], inLen, inLen + n, 0f)
            inLen += n
        }

        val step = hs * speed.toDouble()
        while (true) {
            val s = Math.round(anaPos)
            if (finish) {
                if (s >= totalIn) break
            } else if (s + n > inBase + inLen) {
                break
            }
            processFrame((s - inBase).toInt(), s)
            anaPos += step
        }

        if (finish) {
            emitTail()
        } else {
            // Выбрасываем то, что следующий кадр уже не затронет
            val keep = (Math.round(anaPos) - inBase).coerceIn(0L, inLen.toLong()).toInt()
            if (keep > 0) {
                for (c in 0 until channels) System.arraycopy(inBuf[c], keep, inBuf[c], 0, inLen - keep)
                inBase += keep
                inLen -= keep
            }
        }
    }

    private fun processFrame(offset: Int, start: Long) {
        val ha = if (prevStart < 0) hs else (start - prevStart).toInt().coerceAtLeast(1)
        for (g in groups) g.process(offset, ha)
        for (i in 0 until n) nrm[i] += window2[i]
        prevStart = start

        // Первые hs отсчётов накопителя больше не изменятся: отдаём их и сдвигаем накопитель
        ensureOut(outFrames + hs)
        for (i in 0 until hs) {
            val g = if (nrm[i] > 1e-9) 1.0 / nrm[i] else 0.0
            for (c in 0 until channels) outCh[c][outFrames + i] = (acc[c][i] * g).toFloat()
        }
        outFrames += hs
        for (c in 0 until channels) {
            val a = acc[c]
            System.arraycopy(a, hs, a, 0, n - hs)
            java.util.Arrays.fill(a, n - hs, n, 0.0)
        }
        System.arraycopy(nrm, hs, nrm, 0, n - hs)
        java.util.Arrays.fill(nrm, n - hs, n, 0.0)
    }

    /** Конец трека: отдаём хвост, пока на него накладывается хотя бы часть кадров. */
    private fun emitTail() {
        var cnt = 0
        while (cnt < n && nrm[cnt] > 1.0) cnt++
        ensureOut(outFrames + cnt)
        for (c in 0 until channels) {
            for (i in 0 until cnt) outCh[c][outFrames + i] = (acc[c][i] / nrm[i]).toFloat()
            java.util.Arrays.fill(acc[c], 0.0)
        }
        java.util.Arrays.fill(nrm, 0.0)
        outFrames += cnt
    }

    // ------------------------------------------------------------------
    // Одна стереопара (или одиночный канал): общий фазовый сдвиг на оба канала
    // ------------------------------------------------------------------

    private inner class Group(private val a: Int, private val b: Int) {
        private val stereo = b >= 0
        private val re = DoubleArray(n)
        private val im = DoubleArray(n)

        // Спектры каналов (полуспектр 0..half), «сумма» для расчёта фаз и её предыдущее значение
        private val xlr = DoubleArray(half + 1)
        private val xli = DoubleArray(half + 1)
        private val xrr = DoubleArray(half + 1)
        private val xri = DoubleArray(half + 1)
        private var mr = DoubleArray(half + 1)
        private var mi = DoubleArray(half + 1)
        private var mrPrev = DoubleArray(half + 1)
        private var miPrev = DoubleArray(half + 1)
        private val pw = DoubleArray(half + 1)

        // Фазовый сдвиг, применённый на прошлом кадре, и на текущем (по бинам)
        private var thPrev = DoubleArray(half + 1)
        private var th = DoubleArray(half + 1)
        private val rc = DoubleArray(half + 1)
        private val rs = DoubleArray(half + 1)
        private val peaks = IntArray(half + 1)
        private var hasPrev = false

        fun process(offset: Int, ha: Int) {
            val la = inBuf[a]
            val lb = if (stereo) inBuf[b] else null
            for (i in 0 until n) {
                val w = window[i]
                re[i] = la[offset + i] * w.toDouble()
                im[i] = if (lb != null) lb[offset + i] * w else 0.0
            }
            fft(re, im)

            // Разделяем спектр пары каналов, упакованной в один комплексный сигнал L + jR
            for (k in 0..half) {
                val nk = (n - k) and (n - 1)
                val zr = re[k]
                val zi = im[k]
                if (stereo) {
                    val yr = re[nk]
                    val yi = im[nk]
                    xlr[k] = (zr + yr) * 0.5
                    xli[k] = (zi - yi) * 0.5
                    xrr[k] = (zi + yi) * 0.5
                    xri[k] = -(zr - yr) * 0.5
                    mr[k] = xlr[k] + xrr[k]
                    mi[k] = xli[k] + xri[k]
                } else {
                    xlr[k] = zr
                    xli[k] = zi
                    mr[k] = zr
                    mi[k] = zi
                }
                pw[k] = mr[k] * mr[k] + mi[k] * mi[k]
            }

            // Пики спектра суммы каналов
            var np = 0
            for (k in 0..half) {
                val p = pw[k]
                if (p <= 1e-18) continue
                var ok = true
                for (d in 1..2) {
                    if (k - d >= 0 && pw[k - d] >= p) ok = false
                    if (k + d <= half && pw[k + d] > p) ok = false
                }
                if (ok) peaks[np++] = k
            }

            // Фазовый сдвиг для каждого пика; остальные бины берут сдвиг ближайшего пика
            java.util.Arrays.fill(rc, 1.0)
            java.util.Arrays.fill(rs, 0.0)
            java.util.Arrays.fill(th, 0.0)
            if (hasPrev && np > 0) {
                val ratio = hs.toDouble() / ha
                for (i in 0 until np) {
                    val k = peaks[i]
                    val pr = mr[k] * mrPrev[k] + mi[k] * miPrev[k]
                    val pi = mi[k] * mrPrev[k] - mr[k] * miPrev[k]
                    val d0 = atan2(pi, pr)
                    val omega = 2.0 * PI * k / n
                    val d = wrap(d0 - omega * ha)
                    val t = wrap(thPrev[k] + ratio * d + omega * hs - d0)
                    val c = cos(t)
                    val s = sin(t)
                    val from = if (i == 0) 0 else (peaks[i - 1] + k + 1) / 2
                    val to = if (i == np - 1) half + 1 else (k + peaks[i + 1] + 1) / 2
                    for (j in from until to) {
                        th[j] = t
                        rc[j] = c
                        rs[j] = s
                    }
                }
            }
            // Запоминаем текущее состояние для следующего кадра
            val tmpTh = thPrev; thPrev = th; th = tmpTh
            val tmpR = mrPrev; mrPrev = mr; mr = tmpR
            val tmpI = miPrev; miPrev = mi; mi = tmpI
            hasPrev = true

            // Поворот спектра и сборка комплексного спектра L + jR для обратного БПФ
            for (k in 0..half) {
                var c = rc[k]
                var s = rs[k]
                if (k == 0 || k == half) { // постоянная составляющая и Найквист должны оставаться вещественными
                    c = 1.0
                    s = 0.0
                }
                var ylr = xlr[k] * c - xli[k] * s
                var yli = xlr[k] * s + xli[k] * c
                var yrr = 0.0
                var yri = 0.0
                if (stereo) {
                    yrr = xrr[k] * c - xri[k] * s
                    yri = xrr[k] * s + xri[k] * c
                }
                if (k == 0 || k == half) {
                    yli = 0.0
                    yri = 0.0
                }
                re[k] = ylr - yri
                im[k] = yli + yrr
                if (k in 1 until half) {
                    val j = n - k
                    re[j] = ylr + yri
                    im[j] = -yli + yrr
                }
            }

            // Обратное БПФ через прямое с обменом re/im; результат: re = левый, im = правый
            fft(im, re)
            val inv = 1.0 / n
            val accA = acc[a]
            for (i in 0 until n) accA[i] += re[i] * window[i] * inv
            if (stereo) {
                val accB = acc[b]
                for (i in 0 until n) accB[i] += im[i] * window[i] * inv
            }
        }
    }

    // ------------------------------------------------------------------
    // Вспомогательное
    // ------------------------------------------------------------------

    /** Приводит угол к диапазону [−π, π]. */
    private fun wrap(x: Double): Double {
        var v = x % (2.0 * PI)
        if (v > PI) v -= 2.0 * PI else if (v < -PI) v += 2.0 * PI
        return v
    }

    /** Прямое БПФ (радикс-2, на месте) для вектора длиной n. */
    private fun fft(xr: DoubleArray, xi: DoubleArray) {
        for (i in 0 until n) {
            val j = rev[i]
            if (j > i) {
                val tr = xr[i]; xr[i] = xr[j]; xr[j] = tr
                val ti = xi[i]; xi[i] = xi[j]; xi[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val halfLen = len shr 1
            val step = n / len
            var i = 0
            while (i < n) {
                var t = 0
                for (k in 0 until halfLen) {
                    val wr = cosT[t]
                    val wi = -sinT[t]
                    val p = i + k
                    val q = p + halfLen
                    val tr = xr[q] * wr - xi[q] * wi
                    val ti = xr[q] * wi + xi[q] * wr
                    xr[q] = xr[p] - tr
                    xi[q] = xi[p] - ti
                    xr[p] += tr
                    xi[p] += ti
                    t += step
                }
                i += len
            }
            len = len shl 1
        }
    }
}
