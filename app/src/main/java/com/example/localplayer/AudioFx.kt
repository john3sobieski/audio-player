package com.example.localplayer

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Глобальные настройки обработки звука. Читаются обработчиком звука «на лету»,
 * поэтому переключатели в настройках действуют сразу, без перезапуска плеера.
 */
object AudioFx {
    @Volatile var mono = false
    @Volatile var normalize = false

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        mono = p.getBoolean("mono", false)
        normalize = p.getBoolean("normalize", false)
    }

    fun save(ctx: Context) {
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putBoolean("mono", mono)
            .putBoolean("normalize", normalize)
            .apply()
    }
}

/**
 * Обработчик звука для ExoPlayer.
 *
 * 1. Моно: левый и правый каналы усредняются и отдаются в оба канала.
 * 2. Нормализация: медленная автоподстройка усиления к целевому уровню громкости
 *    (около -16 dBFS RMS) с мягким ограничителем пиков. Тихие треки подтягиваются
 *    (до +9 dB), громкие приглушаются (до -12 dB).
 *
 * Работает с 16-битным PCM и float PCM. Для остальных форматов остаётся неактивным.
 */
@UnstableApi
class AudioFxProcessor : BaseAudioProcessor() {

    private var floatMode = false
    private var channels = 2

    private var env = TARGET_POWER      // сглаженная мощность сигнала
    private var gain = 1.0              // текущее усиление
    private var envAttack = 0.0
    private var envRelease = 0.0
    private var gainAttack = 0.0
    private var gainRelease = 0.0

    private var frame = DoubleArray(2)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val enc = inputAudioFormat.encoding
        if ((enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) || inputAudioFormat.channelCount < 1) {
            return AudioProcessor.AudioFormat.NOT_SET // обработчик неактивен для этого формата
        }
        floatMode = enc == C.ENCODING_PCM_FLOAT
        channels = inputAudioFormat.channelCount
        frame = DoubleArray(channels)

        val sr = inputAudioFormat.sampleRate.coerceAtLeast(8000).toDouble()
        envAttack = coef(sr, 0.25)
        envRelease = coef(sr, 0.8)
        gainAttack = coef(sr, 0.3)
        gainRelease = coef(sr, 2.0)
        return inputAudioFormat
    }

    override fun onFlush() {
        env = TARGET_POWER
        gain = 1.0
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

        val doMono = AudioFx.mono && channels == 2
        val doNorm = AudioFx.normalize

        if (!doMono && !doNorm) {
            gain = 1.0
            env = TARGET_POWER
            out.put(inputBuffer)
            out.flip()
            return
        }

        val bytesPerSample = if (floatMode) 4 else 2
        val frames = size / (channels * bytesPerSample)
        val s = frame

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
            for (c in 0 until channels) {
                val v = s[c]
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

        for (c in s.indices) s[c] = softLimit(s[c] * gain)
    }

    private fun softLimit(x: Double): Double {
        val a = abs(x)
        return if (a <= LIMIT_KNEE) x else sign(x) * (LIMIT_KNEE + (1.0 - LIMIT_KNEE) * tanh((a - LIMIT_KNEE) / (1.0 - LIMIT_KNEE)))
    }

    private fun coef(sampleRate: Double, seconds: Double) = 1.0 - exp(-1.0 / (sampleRate * seconds))

    private companion object {
        const val TARGET_RMS = 0.16           // около -16 dBFS
        const val TARGET_POWER = TARGET_RMS * TARGET_RMS
        const val GATE_POWER = 1e-6           // около -60 dBFS: ниже считаем тишиной
        const val MIN_GAIN = 0.25             // -12 dB
        const val MAX_GAIN = 2.8              // +9 dB
        const val LIMIT_KNEE = 0.9
    }
}
