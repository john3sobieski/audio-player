package com.example.localplayer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// =====================================================================
// Перетаскивание элементов списка (долгое нажатие + движение)
// =====================================================================

/** Обработчики, которые экран передаёт в [ReorderState]. */
class ReorderCallbacks(
    /** Можно ли перетаскивать элемент с таким ключом (заголовки и кнопки списка — нельзя). */
    val canDrag: (Any) -> Boolean,
    /** Начало перетаскивания: экран копирует показанный список в рабочий. */
    val onStart: () -> Unit,
    /** Элемент [from] нужно поставить на место элемента [to] в рабочем списке. */
    val onMove: (from: Any, to: Any) -> Unit,
    /** Конец перетаскивания: экран сохраняет новый порядок. */
    val onEnd: () -> Unit,
)

/**
 * Состояние перетаскивания в LazyColumn. Во время жеста элемент «висит» под пальцем ([offset] — смещение
 * от его места в списке), а соседи расступаются, когда центр элемента заходит на их место.
 * У верхней и нижней кромки список сам прокручивается.
 */
@Stable
class ReorderState internal constructor(
    private val list: LazyListState,
    private val scope: CoroutineScope,
    private val haptics: HapticFeedback,
    private val edgePx: Float,
    private val maxStepPx: Float,
    private val callbacks: androidx.compose.runtime.State<ReorderCallbacks>,
) {
    var draggedKey by mutableStateOf<Any?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set

    val isDragging: Boolean get() = draggedKey != null

    private var scrollJob: Job? = null
    private var settleJob: Job? = null
    private var ending = false

    // После перестановки layout обновляется не сразу: до этого момента новые перестановки не делаем
    private var pendingIndex = -1
    private var pendingSince = 0L

    private val cb get() = callbacks.value

    internal fun onDragStart(key: Any) {
        if (draggedKey != null || !cb.canDrag(key)) return
        settleJob?.cancel()
        ending = false
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        cb.onStart()
        offset = 0f
        pendingIndex = -1
        draggedKey = key
        scrollJob = scope.launch {
            while (true) {
                withFrameNanos { }
                if (draggedKey == null) return@launch
                swapIfNeeded()
                autoScrollStep()
            }
        }
    }

    internal fun onDrag(dy: Float) {
        if (draggedKey == null || ending) return
        offset += dy
        swapIfNeeded()
    }

    internal fun onDragEnd() {
        if (draggedKey == null || ending) return
        ending = true
        scrollJob?.cancel()
        cb.onEnd()
        // Элемент плавно «садится» на своё место
        settleJob = scope.launch {
            Animatable(offset).animateTo(0f, tween(140)) { offset = value }
            draggedKey = null
            offset = 0f
            ending = false
        }
    }

    private fun swapIfNeeded() {
        val key = draggedKey ?: return
        val items = list.layoutInfo.visibleItemsInfo
        val cur = items.firstOrNull { it.key == key } ?: return

        if (pendingIndex >= 0) {
            if (cur.index != pendingIndex && System.nanoTime() - pendingSince < 120_000_000L) return
            pendingIndex = -1
        }

        val center = cur.offset + offset + cur.size / 2f
        val target = items.firstOrNull {
            it.key != key && cb.canDrag(it.key) && center >= it.offset && center < it.offset + it.size
        } ?: return

        val newLayoutOffset = if (target.index > cur.index) target.offset + target.size - cur.size else target.offset
        val firstIndex = list.firstVisibleItemIndex
        val firstOffset = list.firstVisibleItemScrollOffset

        cb.onMove(key, target.key)

        // LazyColumn сам «следует» за переставленным элементом по ключу; возвращаем прежнюю прокрутку
        list.requestScrollToItem(firstIndex, firstOffset)
        offset -= (newLayoutOffset - cur.offset)
        pendingIndex = target.index
        pendingSince = System.nanoTime()
    }

    private suspend fun autoScrollStep() {
        val key = draggedKey ?: return
        val info = list.layoutInfo
        val cur = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        val top = cur.offset + offset
        val bottom = top + cur.size
        val upZone = info.viewportStartOffset + edgePx
        val downZone = info.viewportEndOffset - edgePx
        val dy = when {
            top < upZone -> -maxStepPx * ((upZone - top) / edgePx).coerceIn(0.15f, 1f)
            bottom > downZone -> maxStepPx * ((bottom - downZone) / edgePx).coerceIn(0.15f, 1f)
            else -> 0f
        }
        if (dy != 0f && (dy < 0f && list.canScrollBackward || dy > 0f && list.canScrollForward)) {
            offset += list.scrollBy(dy)
        }
    }

    /** Жесты для строки списка: долгое нажатие запускает перетаскивание. */
    internal fun gestureModifier(key: Any): Modifier = Modifier.pointerInput(key) {
        detectDragGesturesAfterLongPress(
            onDragStart = { onDragStart(key) },
            onDrag = { change, amount ->
                change.consume()
                onDrag(amount.y)
            },
            onDragEnd = { onDragEnd() },
            onDragCancel = { onDragEnd() },
        )
    }
}

@Composable
fun rememberReorderState(list: LazyListState, callbacks: ReorderCallbacks): ReorderState {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val current = rememberUpdatedState(callbacks)
    return remember(list) {
        ReorderState(
            list = list,
            scope = scope,
            haptics = haptics,
            edgePx = with(density) { 72.dp.toPx() },
            maxStepPx = with(density) { 14.dp.toPx() },
            callbacks = current,
        )
    }
}

/**
 * Модификатор строки списка с поддержкой перетаскивания: анимация перестановки соседей,
 * а у поднятой строки — смещение под палец, тень и показ поверх остальных.
 */
@Composable
fun LazyItemScope.reorderableItem(state: ReorderState, key: Any): Modifier {
    val dragged = state.draggedKey == key
    val shadow = with(LocalDensity.current) { 10.dp.toPx() }
    return Modifier
        .animateItem(
            // У поднятой строки анимацию места отключаем: её положение задаёт палец
            placementSpec = if (dragged) null else spring(
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = IntOffset.VisibilityThreshold,
            ),
        )
        .then(
            if (dragged) {
                Modifier
                    .zIndex(1f)
                    .graphicsLayer {
                        translationY = state.offset
                        shadowElevation = shadow
                        shape = RoundedCornerShape(12.dp)
                        clip = false
                    }
            } else Modifier,
        )
        .then(state.gestureModifier(key))
}

// =====================================================================
// Полоска сортировки
// =====================================================================

/**
 * Тонкая полоска над списком: слева выбор типа сортировки, справа переключатель направления
 * («По возрастанию» / «По убыванию»).
 */
@Composable
fun SortBar(
    sort: SortState,
    options: List<SortKey>,
    onChange: (SortState) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        if (sort.descending) 180f else 0f,
        animationSpec = tween(220),
        label = "sortArrow",
    )
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // ----- Тип сортировки -----
            Box(Modifier.weight(1f, fill = false)) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { menuOpen = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Sort, null, Modifier.size(18.dp), tint = muted)
                    Spacer(Modifier.width(6.dp))
                    AnimatedContent(
                        targetState = sort.key,
                        transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) },
                        label = "sortLabel",
                    ) { key ->
                        Text(
                            key.label,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(Icons.Default.ArrowDropDown, null, Modifier.size(20.dp), tint = muted)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    options.forEach { key ->
                        DropdownMenuItem(
                            text = { Text(key.label) },
                            leadingIcon = {
                                if (key == sort.key) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                else Spacer(Modifier.size(24.dp))
                            },
                            onClick = {
                                menuOpen = false
                                if (key != sort.key) onChange(sort.copy(key = key))
                            },
                        )
                    }
                }
            }

            // ----- Направление -----
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onChange(sort.copy(descending = !sort.descending)) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    targetState = sort.descending,
                    transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) },
                    label = "sortDirection",
                ) { desc ->
                    Text(
                        if (desc) "По убыванию" else "По возрастанию",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Default.ArrowUpward, null,
                    Modifier.size(18.dp).graphicsLayer { rotationZ = arrowRotation },
                    tint = muted,
                )
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}
