package com.example.localplayer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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

    override fun onCreate() {
        super.onCreate()
        AudioFx.load(this)

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
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        fader?.cancelFades()
        fader = null
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}

/**
 * Плавный старт и остановка: при запуске громкость быстро нарастает от нуля, при паузе так же убывает,
 * и только после этого воспроизведение реально останавливается. Громкость меняется на уровне плеера
 * (не затрагивает системную громкость и обработку звука). Смена трека во время игры не затрагивается.
 */
private class FadingPlayer(private val exo: ExoPlayer) : ForwardingPlayer(exo) {

    private val handler = Handler(Looper.getMainLooper())
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

    init {
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
            exo.volume = 1f
            super.play()
        } else {
            // Запуск с паузы или с остановки: начинаем с тишины, нарастание стартует, когда звук реально пошёл
            exo.volume = 0f
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
            exo.volume = 1f
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
        if (resetVolume) exo.volume = 1f
    }

    private fun rampTo(target: Float, durationMs: Long, onEnd: (() -> Unit)? = null) {
        ramp?.let { handler.removeCallbacks(it) }
        val from = exo.volume
        val start = SystemClock.uptimeMillis()
        val step = object : Runnable {
            override fun run() {
                val f = ((SystemClock.uptimeMillis() - start).toFloat() / durationMs).coerceIn(0f, 1f)
                exo.volume = from + (target - from) * f
                if (f < 1f) {
                    handler.postDelayed(this, STEP_MS)
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
    }
}
