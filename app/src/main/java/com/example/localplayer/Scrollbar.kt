package com.example.localplayer

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Контейнер для списка с быстрой прокруткой: поверх [content] (LazyColumn с состоянием [state]) рисуется
 * ползунок, за который можно «тянуть» список. Ползунок появляется при прокрутке и плавно гаснет через
 * пару секунд; во время перетаскивания рядом показывается номер элемента.
 *
 * [headerCount] — сколько элементов в начале списка не являются строками (заголовки, кнопки): они не учитываются в номере.
 */
@Composable
fun FastScrollContainer(
    state: LazyListState,
    modifier: Modifier = Modifier.fillMaxSize(),
    headerCount: Int = 0,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier) {
        content()
        FastScrollbar(state, headerCount, Modifier.matchParentSize())
    }
}

private val THUMB_TOUCH_WIDTH = 20.dp   // узкая зона у самого края: не мешает кнопке «⋮» в строках
private val THUMB_TOUCH_HEIGHT = 56.dp
private const val DRAG_SMOOTHING_MS = 70f

@Composable
private fun FastScrollbar(state: LazyListState, headerCount: Int, modifier: Modifier) {
    val density = LocalDensity.current
    val thumbPx = with(density) { THUMB_TOUCH_HEIGHT.toPx() }
    var heightPx by remember { mutableIntStateOf(0) }

    var dragging by remember { mutableStateOf(false) }
    var target by remember { mutableFloatStateOf(0f) }   // куда тянет палец (0..1)
    var smooth by remember { mutableFloatStateOf(0f) }   // сглаженное положение во время перетаскивания

    val scrollable by remember { derivedStateOf { state.canScrollForward || state.canScrollBackward } }
    val listFraction by remember { derivedStateOf { scrollFraction(state) } }
    val animatedFraction by animateFloatAsState(
        listFraction,
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "thumbPos",
    )

    // Показ: при прокрутке и ещё немного после неё
    var shown by remember { mutableStateOf(false) }
    val scrolling = state.isScrollInProgress
    LaunchedEffect(scrolling, dragging) {
        if (scrolling || dragging) shown = true else {
            delay(1500)
            shown = false
        }
    }
    val alpha by animateFloatAsState(
        if (shown && scrollable) 1f else 0f,
        tween(if (shown) 120 else 350),
        label = "thumbAlpha",
    )
    val barWidth by animateDpAsState(if (dragging) 8.dp else 4.dp, tween(120), label = "thumbWidth")

    // Пока тянем: положение ползунка/списка плавно догоняет палец
    LaunchedEffect(dragging) {
        if (!dragging) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dtMs = (now - last) / 1_000_000f
            last = now
            smooth += (target - smooth) * (1f - exp(-dtMs / DRAG_SMOOTHING_MS))
            scrollToFraction(state, smooth)
        }
    }

    Box(modifier.onSizeChanged { heightPx = it.height }) {
        if (alpha > 0.02f && heightPx > thumbPx) {
            val trackPx = heightPx - thumbPx
            val shownFraction = if (dragging) smooth else animatedFraction
            val y = (shownFraction.coerceIn(0f, 1f) * trackPx).roundToInt()

            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(0, y) }
                    .width(THUMB_TOUCH_WIDTH)
                    .height(THUMB_TOUCH_HEIGHT)
                    .graphicsLayer { this.alpha = alpha }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                smooth = scrollFraction(state)
                                target = smooth
                                dragging = true
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                target = (target + amount.y / trackPx).coerceIn(0f, 1f)
                            },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false },
                        )
                    },
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp)
                        .width(barWidth)
                        .height(THUMB_TOUCH_HEIGHT - 16.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = if (dragging) 0.95f else 0.65f)),
                )
            }

            if (dragging) {
                val n = (state.firstVisibleItemIndex - headerCount + 1).coerceAtLeast(1)
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset { IntOffset(-with(density) { 32.dp.roundToPx() }, y + ((thumbPx - with(density) { 32.dp.toPx() }) / 2f).roundToInt()) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shadowElevation = 4.dp,
                ) {
                    Text(
                        n.toString(),
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/** Положение списка как число 0..1 (по индексу первого видимого элемента и сдвигу внутри него). */
private fun scrollFraction(state: LazyListState): Float {
    val info = state.layoutInfo
    val total = info.totalItemsCount
    val visible = info.visibleItemsInfo
    if (total == 0 || visible.isEmpty()) return 0f
    if (!state.canScrollBackward) return 0f
    if (!state.canScrollForward) return 1f
    val first = visible.first()
    val inner = if (first.size > 0) state.firstVisibleItemScrollOffset.toFloat() / first.size else 0f
    val span = max(1f, (total - visible.size).toFloat())
    return ((first.index + inner) / span).coerceIn(0f, 1f)
}

private suspend fun scrollToFraction(state: LazyListState, fraction: Float) {
    val info = state.layoutInfo
    val total = info.totalItemsCount
    if (total == 0) return
    val visible = info.visibleItemsInfo
    val span = max(1f, (total - visible.size).toFloat())
    val pos = fraction.coerceIn(0f, 1f) * span
    val index = floor(pos).toInt().coerceIn(0, total - 1)
    val itemSize = visible.firstOrNull()?.size ?: 0
    state.scrollToItem(index, ((pos - index) * itemSize).roundToInt())
}
