package com.example.localplayer

import android.os.SystemClock
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Строка подсвечивается чуть раньше времени метки: так текст воспринимается синхронным с голосом. */
private const val LEAD_MS = 120L

/** После ручной прокрутки текст не «убегает» из-под пальца: автослежение возвращается через эту паузу. */
private const val RESUME_FOLLOW_MS = 3000L

private const val ALPHA_UPCOMING = 0.55f
private const val ALPHA_PAST = 0.25f

/**
 * Кнопка «Текст песни» под кнопкой «i»: скруглённая рамка с тремя строчками текста,
 * средняя (поющаяся сейчас) выделена цветом темы. Не копирует значок микрофона из Spotify.
 */
@Composable
internal fun LyricsButton(enabled: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(26.dp)) {
            val sw = 1.5.dp.toPx()
            drawRoundRect(
                c, topLeft = Offset(sw / 2, sw / 2), size = Size(size.width - sw, size.height - sw),
                cornerRadius = CornerRadius(8.dp.toPx()), style = Stroke(sw),
            )
            val left = size.width * 0.24f
            val h = 2.dp.toPx()
            val r = CornerRadius(h / 2)
            drawRoundRect(c.copy(alpha = 0.55f), Offset(left, size.height * 0.30f - h / 2), Size(size.width * 0.40f, h), r)
            drawRoundRect(accent, Offset(left, size.height * 0.50f - h / 2), Size(size.width * 0.52f, h), r)
            drawRoundRect(c.copy(alpha = 0.55f), Offset(left, size.height * 0.70f - h / 2), Size(size.width * 0.32f, h), r)
        }
    }
}

/**
 * Окно текста песни. Плеер при этом «сжат» до мини-плеера внизу — как на экранах библиотеки;
 * нажатие на мини-плеер возвращает полный плеер. [miniPlayer] рисуется в самом низу.
 */
@Composable
internal fun LyricsScreen(vm: PlayerViewModel, miniPlayer: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val t = vm.currentTrack
    val lyrics by produceState<LyricsResult?>(null, t?.uri, t?.lyricsUri, t?.tagLyrics) {
        value = null
        value = if (t == null) LyricsResult.None else Lyrics.load(ctx, t)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.pop() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
                Text(
                    "Текст песни",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Crossfade(targetState = lyrics, animationSpec = tween(250), label = "lyricsState") { l ->
                    when (l) {
                        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                        is LyricsResult.None -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "Текст для выбранной песни отсутствует",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                        is LyricsResult.Plain -> PlainLyrics(l.lines)
                        is LyricsResult.Synced -> SyncedLyrics(vm, l.lines)
                    }
                }
            }
            // Сжатый плеер: фон доходит до края экрана под панелью навигации
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).navigationBarsPadding()) {
                miniPlayer()
            }
        }
    }
}

/** Текст без таймкодов: компактно, мелким шрифтом, пустые строки сведены к небольшому просвету. */
@Composable
private fun PlainLyrics(lines: List<String>) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
    ) {
        items(lines) { s ->
            if (s.isEmpty()) {
                Spacer(Modifier.height(12.dp))
            } else {
                Text(
                    s,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                )
            }
        }
    }
}

/** Индекс последней строки, чьё время уже наступило; -1, если ещё не началась первая. */
private fun lineIndexAt(times: LongArray, pos: Long): Int {
    var lo = 0
    var hi = times.size - 1
    var ans = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (times[mid] <= pos) { ans = mid; lo = mid + 1 } else hi = mid - 1
    }
    return ans
}

/**
 * Текст с таймкодами, как в Spotify: крупные строки, текущая яркая, ещё не спетые приглушены,
 * уже спетые ещё тусклее. Приглушение делается прозрачностью цвета текста, поэтому в тёмной теме
 * строки темнеют, а в светлой — светлеют. Нажатие на строку перематывает на неё.
 */
@Composable
private fun SyncedLyrics(vm: PlayerViewModel, lines: List<LyricLine>) {
    val times = remember(lines) { LongArray(lines.size) { lines[it].timeMs } }
    var current by remember(lines) { mutableIntStateOf(-1) }
    val listState = rememberLazyListState()
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    val draggedNow = rememberUpdatedState(dragged)

    // Один цикл: определяет текущую строку и ведёт прокрутку. Позиция читается раз в 100 мс, перекомпозиция
    // происходит только при смене строки.
    LaunchedEffect(lines) {
        var wasDragged = false
        var holdUntil = 0L
        var scrolledTo = Int.MIN_VALUE
        var first = true
        var job: Job? = null
        while (true) {
            val now = SystemClock.elapsedRealtime()
            val pos = (vm.controller?.currentPosition ?: 0L) + LEAD_MS
            val idx = lineIndexAt(times, pos)
            if (idx != current) current = idx

            val d = draggedNow.value
            if (d) {
                wasDragged = true
                holdUntil = Long.MAX_VALUE
                scrolledTo = Int.MIN_VALUE // после паузы вернёмся к текущей строке
            } else if (wasDragged) {
                wasDragged = false
                holdUntil = now + RESUME_FOLLOW_MS
            }
            if (!d && now >= holdUntil && idx != scrolledTo) {
                scrolledTo = idx
                val target = idx.coerceAtLeast(0)
                job?.cancel()
                if (first) {
                    first = false
                    listState.scrollToItem(target)
                } else {
                    job = launch { listState.animateScrollToItem(target) }
                }
            }
            delay(100)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // Сверху отступ в треть экрана: текущая строка встаёт на это место. Снизу запас, чтобы докрутить последние строки.
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = maxHeight * 0.28f, bottom = maxHeight * 0.7f),
        ) {
            itemsIndexed(lines) { i, line ->
                LyricRow(i, current, line.text) { vm.seekTo(line.timeMs) }
            }
        }
    }
}

@Composable
private fun LyricRow(index: Int, current: Int, text: String, onClick: () -> Unit) {
    val targetAlpha = when {
        index == current -> 1f
        current < 0 || index > current -> ALPHA_UPCOMING
        else -> ALPHA_PAST
    }
    val alpha by animateFloatAsState(targetAlpha, tween(350), label = "lyricAlpha")
    val scale by animateFloatAsState(if (index == current) 1f else 0.93f, tween(350), label = "lyricScale")
    Text(
        text.ifEmpty { "♪" },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .padding(vertical = 10.dp),
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 26.sp,
        lineHeight = 33.sp,
        fontWeight = FontWeight.Bold,
    )
}

/** Тонкая полоска прогресса над мини-плеером в окне текста (перематывать по ней нельзя: для этого есть полный плеер). */
@Composable
internal fun MiniProgress(vm: PlayerViewModel, durationMs: Long) {
    val pos = remember { mutableLongStateOf(0L) }
    val total = durationMs.coerceAtLeast(1L)
    LaunchedEffect(vm.currentId) {
        while (true) {
            pos.longValue = vm.controller?.currentPosition ?: 0L
            delay(250)
        }
    }
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Box(
        Modifier.fillMaxWidth().height(2.dp).drawBehind {
            drawRect(inactive)
            val f = (pos.longValue.toFloat() / total).coerceIn(0f, 1f)
            drawRect(active, size = Size(size.width * f, size.height))
        },
    )
}
