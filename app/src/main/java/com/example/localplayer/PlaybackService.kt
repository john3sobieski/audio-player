package com.example.localplayer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** Фоновое воспроизведение: уведомление, кнопки на экране блокировки, гарнитура. */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private var fader: FadingPlayer? = null
    private var exo: ExoPlayer? = null

    // ---- Энергосбережение: громкость и маршрут звука ----
    private var audioManager: AudioManager? = null
    private var routeCallbackRegistered = false
    private val routeCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = applyRoute()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = applyRoute()
    }
    private val powerListener: () -> Unit = {
        fader?.refreshVolume()
        updateRouting()
    }

    override fun onCreate() {
        super.onCreate()
        PowerSave.load(this)
        AudioFx.load(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        // Свой аудиовыход: эффекты (эквалайзер, моно, нормализация) и качественное изменение скорости
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink? =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    // Скорость считаем сами (TimeStretch.kt), а не средствами AudioTrack или встроенным Sonic
                    .setEnableAudioTrackPlaybackParams(false)
                    .setAudioProcessorChain(SpeedAudioProcessorChain(AudioFxProcessor()))
                    .build()
        }

        val player = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        exo = player

        // Сохранённая скорость воспроизведения (меняется кнопкой Speed в плеере)
        player.playbackParameters = PlaybackParameters(AudioFx.effectiveSpeed())

        // Режим «эквалайзер для каждого трека»: при смене трека подставляем его кривую.
        // Делаем это здесь, в сервисе, чтобы работало и при закрытом интерфейсе приложения.
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                AudioFx.setCurrentUri(mediaItem?.mediaId?.takeIf { it.isNotEmpty() })
            }
        })

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Все команды (кнопки в приложении, уведомление, гарнитура) идут через обёртку с плавной громкостью
        fader = FadingPlayer(player)
        session = MediaSession.Builder(this, fader!!).setSessionActivity(openApp).build()

        PowerSave.addListener(powerListener)
        updateRouting()
    }

    /**
     * Интенсивный режим: звук идёт только на проводные наушники / USB-аудио, а если их нет, то на динамик телефона.
     * Bluetooth не используется вовсе (передача по радио заметно расходует заряд и телефона, и наушников).
     * Колбэк подключений регистрируется только в интенсивном режиме, чтобы в остальное время не будить процессор.
     */
    private fun updateRouting() {
        val am = audioManager ?: return
        if (PowerSave.intensiveNow) {
            if (!routeCallbackRegistered) {
                am.registerAudioDeviceCallback(routeCallback, Handler(Looper.getMainLooper()))
                routeCallbackRegistered = true
            }
            applyRoute()
        } else {
            if (routeCallbackRegistered) {
                am.unregisterAudioDeviceCallback(routeCallback)
                routeCallbackRegistered = false
            }
            exo?.setPreferredAudioDevice(null)
        }
    }

    private fun applyRoute() {
        val am = audioManager ?: return
        val outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val wired = outs.firstOrNull { it.type in WIRED_TYPES }
        val speaker = outs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        exo?.setPreferredAudioDevice(wired ?: speaker)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        PowerSave.removeListener(powerListener)
        if (routeCallbackRegistered) {
            audioManager?.unregisterAudioDeviceCallback(routeCallback)
            routeCallbackRegistered = false
        }
        exo = null
        fader?.cancelFades()
        fader = null
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private companion object {
        val WIRED_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL,
        )
    }
}

/**
 * Плавный старт и остановка: при запуске громкость быстро нарастает от нуля, при паузе так же убывает,
 * и только после этого воспроизведение реально останавливается. Громкость меняется на уровне плеера
 * (не затрагивает системную громкость и обработку звука). Смена трека во время игры не затрагивается.
 */
private class FadingPlayer(private val exo: ExoPlayer) : ForwardingPlayer(exo) {

    private val handler = Handler(Looper.getMainLooper())
    /** Уровень плавного старта/остановки (0...1). Реальная громкость плеера = уровень × ограничение энергосбережения. */
    private var level = 1f
    private var ramp: Runnable? = null
    private var fadeInPending = false
    private var pausePending = false

    private val fadeInFallback = Runnable {
        // Звук так и не пошёл (например, ошибка файла): не оставляем плеер с нулевой громкостью
        if (fadeInPending) {
            fadeInPending = false
            rampTo(1f, FADE_IN_MS)
        }
    }

    private fun setLevel(v: Float) {
        level = v
        exo.volume = v * PowerSave.volumeCap
    }

    /** Применяет текущее ограничение громкости (при смене режима энергосбережения). */
    fun refreshVolume() {
        exo.volume = level * PowerSave.volumeCap
    }

    init {
        refreshVolume()
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && fadeInPending) {
                    fadeInPending = false
                    handler.removeCallbacks(fadeInFallback)
                    rampTo(1f, FADE_IN_MS)
                }
            }
        })
    }

    override fun play() {
        if (pausePending) {
            // Пауза ещё не успела наступить: плавно возвращаем громкость с текущего уровня
            cancelFades(resetVolume = false)
            super.play()
            rampTo(1f, FADE_IN_MS)
            return
        }
        cancelFades()
        if (exo.playWhenReady) {
            setLevel(1f)
            super.play()
        } else {
            // Запуск с паузы или с остановки: начинаем с тишины, нарастание стартует, когда звук реально пошёл
            setLevel(0f)
            fadeInPending = true
            handler.postDelayed(fadeInFallback, FADE_IN_FALLBACK_MS)
            super.play()
        }
    }

    override fun pause() {
        if (pausePending) return
        if (!exo.isPlaying) {
            cancelFades()
            super.pause()
            return
        }
        cancelFades(resetVolume = false) // если шло нарастание, уходим вниз с текущего уровня, без скачка
        pausePending = true
        rampTo(0f, FADE_OUT_MS) {
            pausePending = false
            exo.pause()
            setLevel(1f)
        }
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady) play() else pause()
    }

    override fun stop() {
        cancelFades()
        super.stop()
    }

    /** Отменяет текущее изменение громкости; по умолчанию возвращает нормальную громкость. */
    fun cancelFades(resetVolume: Boolean = true) {
        ramp?.let { handler.removeCallbacks(it) }
        ramp = null
        handler.removeCallbacks(fadeInFallback)
        fadeInPending = false
        pausePending = false
        if (resetVolume) setLevel(1f)
    }

    private fun rampTo(target: Float, durationMs: Long, onEnd: (() -> Unit)? = null) {
        ramp?.let { handler.removeCallbacks(it) }
        val from = level
        val start = SystemClock.uptimeMillis()
        val step = object : Runnable {
            override fun run() {
                val f = ((SystemClock.uptimeMillis() - start).toFloat() / durationMs).coerceIn(0f, 1f)
                setLevel(from + (target - from) * f)
                if (f < 1f) {
                    // В режимах энергосбережения громкость меняется реже: меньше пробуждений процессора
                    handler.postDelayed(this, if (PowerSave.reduceNow) STEP_ECO_MS else STEP_MS)
                } else {
                    ramp = null
                    onEnd?.invoke()
                }
            }
        }
        ramp = step
        handler.post(step)
    }

    private companion object {
        const val FADE_IN_MS = 300L
        const val FADE_OUT_MS = 250L
        const val FADE_IN_FALLBACK_MS = 2000L
        const val STEP_MS = 16L
        const val STEP_ECO_MS = 40L
    }
}
