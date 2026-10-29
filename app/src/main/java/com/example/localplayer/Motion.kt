package com.example.localplayer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// =====================================================================
// Единый язык движения приложения
//
//  - нажатие: элемент «вдавливается» (scale) на пружине, без стандартной круглой ряби Android;
//  - отпускание: пружинистый возврат с лёгким «перелётом»;
//  - переключатели: цвет плавно перетекает, значок «подпрыгивает»;
//  - формы и цвета не прыгают, а перетекают друг в друга.
// Все компоненты ниже используют одни и те же пружины, чтобы интерфейс ощущался цельным.
// =====================================================================

/** Пружина нажатия: быстрая, с небольшим отскоком. */
private fun <T> pressSpring() = spring<T>(dampingRatio = 0.5f, stiffness = 650f)

/** Масштаб при нажатии. Применять до clickable/clip, чтобы масштабировался весь элемент. */
@Composable
fun Modifier.pressScale(source: InteractionSource, pressedScale: Float = 0.88f): Modifier {
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) pressedScale else 1f, pressSpring(), label = "pressScale")
    return this.graphicsLayer {
        scaleX = s
        scaleY = s
    }
}

/** Кнопка без рябы: вдавливается при нажатии. Может иметь фон и любую форму (в том числе анимируемую). */
@Composable
fun PressBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pressedScale: Float = 0.88f,
    shape: Shape = CircleShape,
    color: Color = Color.Transparent,
    content: @Composable BoxScope.() -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(src, pressedScale)
            .clip(shape)
            .background(color, shape)
            .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * Нажатие на строку списка (трек, альбом, плейлист): строка слегка вдавливается, а под ней вспыхивает
 * скруглённая подсветка акцентным цветом, которая медленно гаснет. [highlighted] — постоянная лёгкая
 * подсветка (например, играющий трек). ListItem внутри должен быть с прозрачным фоном.
 */
@Composable
fun Modifier.pressableRow(onClick: () -> Unit, highlighted: Boolean = false): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val primary = MaterialTheme.colorScheme.primary
    val bg by animateColorAsState(
        when {
            pressed -> primary.copy(alpha = 0.18f)
            highlighted -> primary.copy(alpha = 0.09f)
            else -> Color.Transparent
        },
        tween(if (pressed) 80 else 380),
        label = "rowBg",
    )
    val s by animateFloatAsState(if (pressed) 0.972f else 1f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "rowScale")
    val shape = RoundedCornerShape(16.dp)
    return this
        .graphicsLayer {
            scaleX = s
            scaleY = s
        }
        .padding(horizontal = 6.dp)
        .clip(shape)
        .background(bg, shape)
        .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onClick)
}

// ---------------------------------------------------------------------
// Play / Pause
// ---------------------------------------------------------------------

/** Значок, который перетекает из треугольника «play» в две полоски «pause» (и обратно) с пружинным отскоком. */
@Composable
fun PlayPauseIcon(playing: Boolean, tint: Color, iconSize: Dp, modifier: Modifier = Modifier) {
    val t by animateFloatAsState(if (playing) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 380f), label = "playPause")
    Canvas(modifier.size(iconSize)) {
        val k = this.size.minDimension / 24f
        // Треугольник разрезан на две части, каждая — четырёхугольник, как и полоска паузы
        val playL = floatArrayOf(7f, 5f, 13f, 8.5f, 13f, 15.5f, 7f, 19f)
        val pauseL = floatArrayOf(6f, 5f, 10f, 5f, 10f, 19f, 6f, 19f)
        val playR = floatArrayOf(13f, 8.5f, 19f, 12f, 19f, 12f, 13f, 15.5f)
        val pauseR = floatArrayOf(14f, 5f, 18f, 5f, 18f, 19f, 14f, 19f)
        fun quad(a: FloatArray, b: FloatArray): Path {
            val p = Path()
            for (i in 0 until 4) {
                val x = (a[2 * i] + (b[2 * i] - a[2 * i]) * t) * k
                val y = (a[2 * i + 1] + (b[2 * i + 1] - a[2 * i + 1]) * t) * k
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close()
            return p
        }
        val round = Stroke(width = 1.8f * k, join = StrokeJoin.Round) // скругляет углы
        for (p in listOf(quad(playL, pauseL), quad(playR, pauseR))) {
            drawPath(p, tint)
            drawPath(p, tint, style = round)
        }
    }
}

/** Главная кнопка: круг при паузе, «мягкий квадрат» при игре; форма перетекает на пружине. */
@Composable
fun PlayPauseButton(playing: Boolean, onClick: () -> Unit, buttonSize: Dp, iconSize: Dp, modifier: Modifier = Modifier) {
    val corner by animateDpAsState(
        if (playing) buttonSize * 0.32f else buttonSize / 2,
        spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "playCorner",
    )
    val cs = MaterialTheme.colorScheme
    PressBox(
        onClick = onClick,
        modifier = modifier.size(buttonSize),
        pressedScale = 0.9f,
        shape = RoundedCornerShape(corner.coerceIn(0.dp, buttonSize / 2)),
        color = cs.primary,
    ) {
        PlayPauseIcon(playing, cs.onPrimary, iconSize)
    }
}

/** «Предыдущий» / «Следующий»: при нажатии значок толкается в свою сторону и пружинисто возвращается. */
@Composable
fun SkipButton(
    icon: ImageVector,
    description: String,
    direction: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    boxSize: Dp = 52.dp,
    iconSize: Dp = 32.dp,
) {
    val nudge = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    PressBox(
        onClick = {
            onClick()
            scope.launch {
                nudge.snapTo(0f)
                nudge.animateTo(direction * 9f, tween(90, easing = FastOutSlowInEasing))
                nudge.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 450f))
            }
        },
        modifier = modifier.size(boxSize),
        pressedScale = 0.86f,
    ) {
        Icon(
            icon, description,
            Modifier.size(iconSize).graphicsLayer { translationX = nudge.value.dp.toPx() },
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Кнопка-переключатель (перемешать, повтор): цвет перетекает, значок «подпрыгивает», снизу появляется точка. */
@Composable
fun ToggleIconButton(
    active: Boolean,
    stateKey: Any,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (tint: Color) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val tint by animateColorAsState(if (active) cs.primary else cs.onSurfaceVariant, tween(220), label = "toggleTint")
    val pop = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(stateKey) {
        if (first) {
            first = false
        } else {
            pop.snapTo(0.6f)
            pop.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = 420f))
        }
    }
    val dot by animateFloatAsState(if (active) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "toggleDot")
    PressBox(onClick = onClick, modifier = modifier.size(48.dp), pressedScale = 0.8f) {
        Box(Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }) { content(tint) }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 5.dp)
                .size(4.dp)
                .graphicsLayer {
                    scaleX = dot
                    scaleY = dot
                    alpha = dot.coerceIn(0f, 1f)
                }
                .background(cs.primary, CircleShape),
        )
    }
}

/**
 * Крупная «таблетка» действия («Играть», «Вперемешку»): вдавливается при нажатии, а значок на пружине
 * подпрыгивает (или, при [spin], делает оборот с отскоком).
 */
@Composable
fun ActionPill(
    label: String,
    icon: ImageVector,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    spin: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    val anim = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val bg by animateColorAsState(
        when {
            !enabled -> cs.onSurface.copy(alpha = 0.12f)
            primary -> cs.primary
            else -> cs.secondaryContainer
        },
        tween(200), label = "pillBg",
    )
    val fg = when {
        !enabled -> cs.onSurface.copy(alpha = 0.38f)
        primary -> cs.onPrimary
        else -> cs.onSecondaryContainer
    }
    PressBox(
        onClick = {
            onClick()
            scope.launch {
                anim.snapTo(0f)
                anim.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = if (spin) 120f else 260f))
            }
        },
        modifier = modifier.height(42.dp),
        enabled = enabled,
        pressedScale = 0.93f,
        shape = RoundedCornerShape(21.dp),
        color = bg,
    ) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon, null,
                Modifier.size(20.dp).graphicsLayer {
                    if (spin) {
                        rotationZ = anim.value * 360f
                    } else {
                        val k = 1.4f - 0.4f * anim.value
                        scaleX = k
                        scaleY = k
                    }
                },
                tint = fg,
            )
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
        }
    }
}

// ---------------------------------------------------------------------
// Сердечко
// ---------------------------------------------------------------------

/**
 * Сердечко: при добавлении «выпрыгивает» с пружинным отскоком и рассыпает кольцо и искры; при снятии
 * коротко сжимается. Смена трека не запускает анимацию (сравнение идёт внутри одного [trackKey]).
 */
@Composable
fun AnimatedHeart(saved: Boolean, trackKey: Any?, enabled: Boolean, description: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val tint by animateColorAsState(if (saved) cs.primary else cs.onSurfaceVariant, tween(250), label = "heartTint")
    val scale = remember { Animatable(1f) }
    val burst = remember { Animatable(0f) }
    var prev by remember(trackKey) { mutableStateOf(saved) }
    LaunchedEffect(trackKey, saved) {
        val changed = saved != prev
        prev = saved
        if (changed) {
            if (saved) {
                launch {
                    burst.snapTo(0f)
                    burst.animateTo(1f, tween(650, easing = LinearOutSlowInEasing))
                }
                scale.snapTo(0.5f)
                scale.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 380f))
            } else {
                scale.snapTo(1f)
                scale.animateTo(0.72f, tween(90))
                scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 500f))
            }
        }
    }
    val c1 = cs.primary
    val c2 = cs.tertiary
    PressBox(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .drawBehind {
                val p = burst.value
                if (p > 0f && p < 1f) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(
                        c1, radius = 9.dp.toPx() + p * 17.dp.toPx(), center = center,
                        alpha = (1f - p) * 0.6f, style = Stroke(width = (3f * (1f - p) + 0.5f).dp.toPx()),
                    )
                    for (i in 0 until 8) {
                        val ang = i * (2 * PI / 8) + PI / 8
                        val r = 8.dp.toPx() + p * 22.dp.toPx()
                        drawCircle(
                            if (i % 2 == 0) c1 else c2,
                            radius = (3.4f * (1f - p)).dp.toPx(),
                            center = Offset(center.x + (cos(ang) * r).toFloat(), center.y + (sin(ang) * r).toFloat()),
                            alpha = 1f - p,
                        )
                    }
                }
            },
        enabled = enabled,
        pressedScale = 0.82f,
    ) {
        Icon(
            if (saved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        )
    }
}

// ---------------------------------------------------------------------
// Переключатель режимов (темы, режимы эквалайзера)
// ---------------------------------------------------------------------

/**
 * Сегментированный переключатель со скользящей «плашкой»: она на пружине перетекает под выбранный пункт,
 * цвет текста и размер значка меняются плавно.
 */
@Composable
fun SlidingSelector(
    labels: List<String>,
    icons: List<ImageVector>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(cs.surfaceContainerHigh)
            .padding(4.dp),
    ) {
        val segW = maxWidth / labels.size
        val x by animateDpAsState(segW * selected, spring(dampingRatio = 0.72f, stiffness = 420f), label = "thumbX")
        Box(
            Modifier
                .offset(x = x)
                .width(segW)
                .height(52.dp)
                .background(cs.primaryContainer, RoundedCornerShape(18.dp)),
        )
        Row {
            labels.forEachIndexed { i, label ->
                val sel = i == selected
                val c by animateColorAsState(if (sel) cs.onPrimaryContainer else cs.onSurfaceVariant, tween(220), label = "segColor")
                val sc by animateFloatAsState(if (sel) 1.15f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "segScale")
                val src = remember { MutableInteractionSource() }
                Column(
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .pressScale(src, 0.93f)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(interactionSource = src, indication = null, role = Role.Tab) { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(icons[i], null, Modifier.size(18.dp).graphicsLayer { scaleX = sc; scaleY = sc }, tint = c)
                    Spacer(Modifier.height(2.dp))
                    Text(label, style = MaterialTheme.typography.labelMedium, color = c, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Пояснение под переключателем: при смене текст выезжает снизу и проявляется. */
@Composable
fun SelectorCaption(text: String) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (fadeIn(tween(240, delayMillis = 70)) + slideInVertically(tween(280)) { it / 3 }) togetherWith fadeOut(tween(110))
        },
        label = "caption",
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
    ) { s ->
        Text(s, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------------
// Меню «три точки»
// ---------------------------------------------------------------------

/** Кнопка «⋮» и всплывающее меню: сильно скруглённая карточка с тенью, пункты со значками выезжают по очереди. */
@Composable
fun AppMenu(items: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Box {
        PressBox(onClick = { open = true }, modifier = Modifier.size(40.dp), pressedScale = 0.82f) {
            Icon(Icons.Default.MoreVert, contentDescription = "Меню", tint = cs.onSurfaceVariant)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = cs.surfaceContainerHigh,
            tonalElevation = 0.dp,
            shadowElevation = 16.dp,
            offset = DpOffset(0.dp, 4.dp),
        ) {
            items.forEachIndexed { i, (label, action) ->
                AppMenuItem(i, label) {
                    open = false
                    action()
                }
            }
        }
    }
}

@Composable
private fun AppMenuItem(index: Int, label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val danger = label.startsWith("Удалить") || label.startsWith("Убрать")
    val color = if (danger) cs.error else cs.onSurface
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 35L)
        appear.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
    }
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val bg by animateColorAsState(if (pressed) color.copy(alpha = 0.12f) else Color.Transparent, tween(if (pressed) 60 else 250), label = "menuBg")
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .padding(horizontal = 8.dp)
            .graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * 10.dp.toPx()
            }
            .pressScale(src, 0.95f)
            .clip(shape)
            .background(bg, shape)
            .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onClick)
            .widthIn(min = 176.dp)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(menuIcon(label), null, Modifier.size(20.dp), tint = color)
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

private fun menuIcon(label: String): ImageVector = when {
    label.startsWith("Переименовать") -> Icons.Default.Edit
    label.startsWith("Удалить") -> Icons.Default.Delete
    label.startsWith("Убрать") -> Icons.Default.RemoveCircleOutline
    label.startsWith("Добавить") -> Icons.AutoMirrored.Filled.PlaylistAdd
    else -> Icons.Default.MoreVert
}

// ---------------------------------------------------------------------
// Плавная смена темы
// ---------------------------------------------------------------------

/** Цвета темы перетекают друг в друга, а не меняются скачком (используется при переключении светлой/тёмной темы). */
@Composable
fun ColorScheme.animated(): ColorScheme {
    val spec = tween<Color>(500)
    @Composable
    fun a(c: Color): Color = animateColorAsState(c, spec, label = "theme").value
    return copy(
        primary = a(primary), onPrimary = a(onPrimary),
        primaryContainer = a(primaryContainer), onPrimaryContainer = a(onPrimaryContainer),
        secondary = a(secondary), onSecondary = a(onSecondary),
        secondaryContainer = a(secondaryContainer), onSecondaryContainer = a(onSecondaryContainer),
        tertiary = a(tertiary), onTertiary = a(onTertiary),
        tertiaryContainer = a(tertiaryContainer), onTertiaryContainer = a(onTertiaryContainer),
        background = a(background), onBackground = a(onBackground),
        surface = a(surface), onSurface = a(onSurface),
        surfaceVariant = a(surfaceVariant), onSurfaceVariant = a(onSurfaceVariant),
        outline = a(outline), outlineVariant = a(outlineVariant),
        error = a(error), onError = a(onError),
        errorContainer = a(errorContainer), onErrorContainer = a(onErrorContainer),
        surfaceContainerLowest = a(surfaceContainerLowest), surfaceContainerLow = a(surfaceContainerLow),
        surfaceContainer = a(surfaceContainer), surfaceContainerHigh = a(surfaceContainerHigh),
        surfaceContainerHighest = a(surfaceContainerHighest),
    )
}
