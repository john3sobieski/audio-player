package com.example.localplayer

import android.content.Context
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import java.util.concurrent.CopyOnWriteArrayList

/** Режим энергоэффективности. По умолчанию — [OFF]: всё работает как обычно. */
enum class PowerMode(val label: String, val description: String) {
    OFF(
        "Отключено",
        "Всё работает по стандарту",
    ),
    MEDIUM(
        "Средний",
        "Почти без анимаций (остаётся только то, что подтверждает действие), индикаторы и текст песни " +
            "обновляются реже, экран не удерживается включённым",
    ),
    INTENSIVE(
        "Интенсивный",
        "Всё из среднего, а также:\n" +
            "• всегда тёмная тема\n" +
            "• громкость плеера не выше 40%\n" +
            "• только моно\n" +
            "• скорость, эквалайзер, басы, требл, subsonic и нормализация заблокированы\n" +
            "• звук только через провод или динамик телефона, без Bluetooth\n" +
            "• папка сканируется только по кнопке\n" +
            "• меньше кэша обложек и заранее собранных страниц",
    ),
}

/**
 * Единая точка правды для энергосбережения.
 *
 *  - [mode] — состояние Compose: интерфейс, который его читает, сам перерисуется при смене режима;
 *  - [intensiveNow] — то же значение для аудиопотока и сервиса (обычное volatile-поле, без обращения к снимкам Compose);
 *  - слушатели ([addListener]) нужны сервису воспроизведения: пересчитать громкость и маршрут звука.
 *
 * Про WakeLock: в приложении нет ни WakeLock'ов, ни keepScreenOn, ни FLAG_KEEP_SCREEN_ON, а у ExoPlayer не включён
 * setWakeMode, поэтому экран и так гаснет во время игры. Отдельного кода для этого не требуется.
 */
object PowerSave {

    /** Максимальная громкость плеера в интенсивном режиме (доля от обычной). */
    const val VOLUME_CAP = 0.4f

    private val state = mutableStateOf(PowerMode.OFF)
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private var loaded = false

    @Volatile
    var intensiveNow = false
        private set

    @Volatile
    var reduceNow = false
        private set

    /** Текущий режим (читается в Compose). */
    val mode: PowerMode get() = state.value

    /** Средний или интенсивный: анимации урезаны. Читается в Compose и в обычном коде. */
    val reduceMotion: Boolean get() = state.value != PowerMode.OFF

    /** Интенсивный режим (читается в Compose). */
    val intensive: Boolean get() = state.value == PowerMode.INTENSIVE

    /** Ограничение громкости плеера: 1 — без ограничения. */
    val volumeCap: Float get() = if (intensiveNow) VOLUME_CAP else 1f

    /** Как часто (мс) обновлять индикаторы прогресса и подсветку строки текста; 0 — на каждом кадре, как раньше. */
    fun progressTickMs(): Long = when (mode) {
        PowerMode.OFF -> 0L
        PowerMode.MEDIUM -> 250L
        PowerMode.INTENSIVE -> 1000L
    }

    /** Период опроса позиции для синхронного текста песни. */
    fun lyricsTickMs(): Long = when (mode) {
        PowerMode.OFF -> 100L
        PowerMode.MEDIUM -> 150L
        PowerMode.INTENSIVE -> 300L
    }

    /** Сколько страниц библиотеки собирать заранее (по бокам от текущей). */
    fun pagerBeyondCount(): Int = if (intensive) 0 else 2

    @Synchronized
    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val saved = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString("power_mode", null)
        val m = runCatching { PowerMode.valueOf(saved ?: "") }.getOrDefault(PowerMode.OFF)
        publish(m)
    }

    /** Меняет режим, сохраняет его и уведомляет звук и сервис. Вызывать из главного потока. */
    fun set(ctx: Context, m: PowerMode) {
        load(ctx)
        if (state.value == m) return
        ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putString("power_mode", m.name).apply()
        publish(m)
        // Обработчик звука пересоберёт фильтры (в интенсивном режиме они отключаются) и применит моно
        AudioFx.refresh()
        listeners.forEach { it() }
    }

    private fun publish(m: PowerMode) {
        intensiveNow = m == PowerMode.INTENSIVE
        reduceNow = m != PowerMode.OFF
        state.value = m
    }

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }
}

// ---------------------------------------------------------------------
// Анимации, которые сами «выключаются» в режимах энергосбережения.
// Вместо tween/spring во всём интерфейсе используются mtween/mspring: в среднем и интенсивном режиме они
// возвращают мгновенный переход (snap), то есть состояние меняется сразу, без покадровой анимации.
// ---------------------------------------------------------------------

fun calmEnter(normal: EnterTransition): EnterTransition =
    if (PowerSave.reduceMotion) EnterTransition.None else normal

fun calmExit(normal: ExitTransition): ExitTransition =
    if (PowerSave.reduceMotion) ExitTransition.None else normal

/** Длительность короткого проявления, которое остаётся при переходах между экранами в режимах энергосбережения. */
const val QUICK_FADE_MS = 90

fun <T> mtween(
    durationMillis: Int = 300,
    delayMillis: Int = 0,
    easing: Easing = FastOutSlowInEasing,
): FiniteAnimationSpec<T> =
    if (PowerSave.reduceMotion) snap() else tween(durationMillis, delayMillis, easing)

fun <T> mspring(
    dampingRatio: Float = Spring.DampingRatioNoBouncy,
    stiffness: Float = Spring.StiffnessMedium,
    visibilityThreshold: T? = null,
): FiniteAnimationSpec<T> =
    if (PowerSave.reduceMotion) snap() else spring(dampingRatio, stiffness, visibilityThreshold)
