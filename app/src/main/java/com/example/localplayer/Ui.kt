package com.example.localplayer

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.semantics.Role
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Waves
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

// =====================================================================
// Анимации переходов
// =====================================================================

/** Экран + глубина стека: по глубине понимаем, идём мы «вперёд» или «назад». */
private data class NavState(val screen: Screen?, val depth: Int)

/**
 * Переходы между экранами сделаны «лёгкими»: полупрозрачным (а значит дорогим для отрисовки)
 * бывает только входящий экран, а уходящий просто сдвигается. Так на кадр приходится
 * один слой с прозрачностью вместо двух.
 */
private fun screenTransition(from: NavState, to: NavState): ContentTransform =
    if (PowerSave.reduceMotion) {
        // Энергосбережение: вместо слайда короткое проявление нового экрана, старый исчезает сразу
        ContentTransform(
            fadeIn(tween(QUICK_FADE_MS)),
            fadeOut(snap()),
            targetContentZIndex = if (to.depth > from.depth) 0f else -1f,
        )
    } else if (to.depth > from.depth) {
        // Вперёд: новый экран выезжает справа поверх старого
        ContentTransform(
            slideInHorizontally(mtween(300)) { it / 4 } + fadeIn(mtween(250)),
            slideOutHorizontally(mtween(300)) { -it / 4 },
        )
    } else {
        // Назад: уходящий экран уезжает вправо, открывая предыдущий под ним
        ContentTransform(
            slideInHorizontally(mtween(300)) { -it / 4 } + fadeIn(mtween(250)),
            slideOutHorizontally(mtween(300)) { it },
            targetContentZIndex = -1f,
        )
    }

// =====================================================================
// Корень приложения
// =====================================================================

@Composable
fun App(vm: PlayerViewModel) {
    // Полноэкранный плеер живёт отдельным слоем поверх всего приложения и не входит в обычную навигацию.
    val playerOpen = Screen.Player in vm.stack
    val playerEqOpen = vm.screen == Screen.PlayerEqualizer
    val lyricsOpen = vm.screen == Screen.Lyrics
    val base = vm.stack.lastOrNull { it !is Screen.Player && it !is Screen.PlayerEqualizer && it !is Screen.Lyrics }
    val depth = vm.stack.count { it !is Screen.Player && it !is Screen.PlayerEqualizer && it !is Screen.Lyrics }

    BackHandler(enabled = vm.screen != null) { vm.pop() }
    BackHandler(enabled = vm.searchOpen && vm.screen == null) { vm.closeSearch() }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.selectFolder(uri)
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = { TopBar(vm, base) },
            bottomBar = {
                // Высота нижних панелей не анимируется: иначе весь экран пересчитывает размеры на каждом кадре перехода
                Column {
                    AnimatedVisibility(
                        visible = vm.currentId != null,
                        enter = slideInVertically(mtween(250)) { it },
                        exit = slideOutVertically(mtween(200)) { it },
                    ) {
                        MiniPlayer(vm)
                    }
                    LibraryNavBar(vm)
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                AnimatedContent(
                    targetState = NavState(base, depth),
                    transitionSpec = { screenTransition(initialState, targetState) },
                    label = "screens",
                ) { state ->
                    // Непрозрачный фон, чтобы при переходе экраны не просвечивали друг через друга
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        when (val s = state.screen) {
                            null -> LibraryScreen(vm) { folderPicker.launch(null) }
                            Screen.Settings -> SettingsScreen(vm) { folderPicker.launch(null) }
                            Screen.Equalizer -> EqualizerScreen(vm, fromPlayer = false)
                            Screen.Player, Screen.PlayerEqualizer, Screen.Lyrics -> Unit // рисуются отдельными слоями ниже
                            is Screen.AlbumScreen -> AlbumScreen(vm, s.key)
                            is Screen.PlaylistScreen -> PlaylistScreen(vm, s.id)
                        }
                    }
                }
            }
        }

        // Плеер выезжает снизу поверх всего. Основной экран под ним не меняется и не перерисовывается.
        AnimatedVisibility(
            visible = playerOpen,
            enter = if (PowerSave.reduceMotion) fadeIn(tween(QUICK_FADE_MS)) else slideInVertically(mtween(350)) { it },
            exit = if (PowerSave.reduceMotion) ExitTransition.None else slideOutVertically(mtween(300)) { it },
        ) {
            PlayerScreen(vm)
        }

        // Эквалайзер из плеера выезжает справа поверх плеера. Кнопка «Назад» возвращает в плеер.
        AnimatedVisibility(
            visible = playerEqOpen,
            enter = if (PowerSave.reduceMotion) fadeIn(tween(QUICK_FADE_MS)) else slideInHorizontally(mtween(300)) { it },
            exit = if (PowerSave.reduceMotion) ExitTransition.None else slideOutHorizontally(mtween(250)) { it },
        ) {
            PlayerEqualizerScreen(vm)
        }

        // Текст песни выезжает снизу поверх плеера; плеер при этом сжат до мини-плеера внизу окна текста.
        AnimatedVisibility(
            visible = lyricsOpen,
            enter = if (PowerSave.reduceMotion) fadeIn(tween(QUICK_FADE_MS)) else slideInVertically(mtween(350)) { it } + fadeIn(mtween(250)),
            exit = if (PowerSave.reduceMotion) ExitTransition.None else slideOutVertically(mtween(300)) { it } + fadeOut(mtween(200)),
        ) {
            LyricsScreen(vm) { MiniPlayer(vm, onClick = { vm.pop() }, showProgress = true) }
        }
    }

    // ----- Диалоги -----
    vm.trackForPlaylist?.let { t ->
        AddToPlaylistDialog(vm, t)
    }
    vm.trackForQueue?.let { t ->
        AddToQueueDialog(vm, t)
    }
    if (vm.newPlaylistDialog) {
        NameDialog(
            title = stringResource(R.string.new_playlist),
            initial = "",
            onConfirm = { vm.createPlaylist(it, vm.newPlaylistFirstTrack); vm.newPlaylistDialog = false; vm.newPlaylistFirstTrack = null },
            onDismiss = { vm.newPlaylistDialog = false; vm.newPlaylistFirstTrack = null },
        )
    }
    vm.renameTarget?.let { p ->
        NameDialog(
            title = stringResource(R.string.rename_playlist),
            initial = p.name,
            onConfirm = { vm.renamePlaylist(p.id, it); vm.renameTarget = null },
            onDismiss = { vm.renameTarget = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(vm: PlayerViewModel, screen: Screen?) {
    val title = when (screen) {
        null -> stringResource(R.string.title_my_music)
        Screen.Settings -> stringResource(R.string.title_settings)
        Screen.Equalizer -> stringResource(R.string.title_equalizer)
        Screen.Player, Screen.PlayerEqualizer, Screen.Lyrics -> "" // у экранов плеера собственная шапка, сюда они не попадают
        is Screen.AlbumScreen -> vm.albums.find { it.key == screen.key }?.let { albumText(it.name) } ?: ""
        is Screen.PlaylistScreen -> vm.playlists.find { it.id == screen.id }?.let { it.label() } ?: ""
    }
    val searching = vm.searchOpen && screen == null
    // 0 -> 1: заголовок уезжает влево и гаснет, на его месте раскрывается поле поиска
    val shift by animateFloatAsState(if (searching) 1f else 0f, mtween(320), label = "titleShift")
    val barColors = MaterialTheme.colorScheme
    // У самого TopAppBar цвет фона внутри анимируется собственной пружиной (поверх нашей анимации темы),
    // из-за чего плашка запаздывала. Поэтому фон рисуем сами, а контейнер TopAppBar делаем прозрачным.
    Surface(color = barColors.surface) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
            navigationIconContentColor = barColors.onSurface,
            titleContentColor = barColors.onSurface,
            actionIconContentColor = barColors.onSurfaceVariant,
        ),
        title = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                AnimatedContent(
                    targetState = title,
                    modifier = Modifier.graphicsLayer {
                        translationX = -(size.width + 64.dp.toPx()) * shift
                        alpha = 1f - shift
                    },
                    transitionSpec = { fadeIn(mtween(200)) togetherWith fadeOut(mtween(150)) },
                    label = "title",
                ) { text ->
                    Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                AnimatedVisibility(
                    visible = searching,
                    enter = fadeIn(mtween(250, delayMillis = 120)) + expandHorizontally(mtween(320), expandFrom = Alignment.End),
                    exit = fadeOut(mtween(150)) + shrinkHorizontally(mtween(250), shrinkTowards = Alignment.End),
                ) {
                    SearchField(vm)
                }
            }
        },
        navigationIcon = {
            AnimatedVisibility(
                visible = screen != null,
                enter = calmEnter(fadeIn() + expandIn()),
                exit = calmExit(shrinkOut() + fadeOut()),
            ) {
                IconButton(onClick = { vm.pop() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }
        },
        actions = {
            // Лупа стоит слева от шестерёнки; при открытом поиске превращается в крестик
            AnimatedVisibility(
                visible = screen == null && vm.folder != null,
                enter = calmEnter(fadeIn() + expandHorizontally()),
                exit = calmExit(shrinkHorizontally() + fadeOut()),
            ) {
                IconButton(onClick = { if (searching) vm.closeSearch() else vm.openSearch() }) {
                    Crossfade(targetState = searching, animationSpec = mtween(300), label = "searchIcon") { isOpen ->
                        Icon(
                            if (isOpen) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = stringResource(if (isOpen) R.string.cd_close_search else R.string.cd_search),
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = screen == null,
                enter = calmEnter(fadeIn() + expandHorizontally()),
                exit = calmExit(shrinkHorizontally() + fadeOut()),
            ) {
                IconButton(onClick = { vm.push(Screen.Settings) }) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.title_settings))
                }
            }
        },
    )
    }
}

/** Поле поиска в верхней панели. Ищет по текущей вкладке: плейлисты, альбомы или треки. */
@Composable
private fun SearchField(vm: PlayerViewModel) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { focus.requestFocus() }
    }
    TextField(
        value = vm.searchQuery,
        onValueChange = { vm.searchQuery = it },
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        singleLine = true,
        placeholder = {
            Text(
                when (vm.tab) {
                    0 -> stringResource(R.string.search_playlists)
                    1 -> stringResource(R.string.search_albums)
                    2 -> stringResource(R.string.search_queue)
                    else -> stringResource(R.string.search_tracks)
                },
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
    )
}

@Composable
private fun LibraryNavBar(vm: PlayerViewModel) {
    val cs = MaterialTheme.colorScheme
    val items = listOf(
        stringResource(R.string.tab_playlists) to Icons.AutoMirrored.Filled.PlaylistPlay,
        stringResource(R.string.tab_albums) to Icons.Default.Album,
        stringResource(R.string.tab_queues) to Icons.AutoMirrored.Filled.QueueMusic,
        stringResource(R.string.tab_tracks) to Icons.Default.LibraryMusic,
    )
    Surface(color = cs.surfaceContainer) {
        BoxWithConstraints(Modifier.navigationBarsPadding().fillMaxWidth().height(80.dp)) {
            val itemW = maxWidth / items.size
            // Одна общая «плашка» выбранного раздела: при смене раздела она не исчезает, а переезжает на пружине
            val pillX by animateDpAsState(
                itemW * vm.tab + (itemW - 64.dp) / 2,
                mspring(dampingRatio = 0.72f, stiffness = 420f),
                label = "navPill",
            )
            Box(
                Modifier
                    .offset(x = pillX, y = 12.dp)
                    .size(width = 64.dp, height = 32.dp)
                    .background(cs.secondaryContainer, RoundedCornerShape(16.dp)),
            )
            Row(Modifier.fillMaxSize()) {
                items.forEachIndexed { i, (label, icon) ->
                    val selected = vm.tab == i
                    val iconColor by animateColorAsState(if (selected) cs.onSecondaryContainer else cs.onSurfaceVariant, mtween(220), label = "navIcon")
                    val textColor by animateColorAsState(if (selected) cs.onSurface else cs.onSurfaceVariant, mtween(220), label = "navText")
                    val iconScale by animateFloatAsState(if (selected) 1.12f else 1f, mspring(dampingRatio = 0.5f, stiffness = 500f), label = "navScale")
                    val src = remember { MutableInteractionSource() }
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pressScale(src, 0.93f)
                            .clickable(interactionSource = src, indication = null, role = Role.Tab) { vm.openTab(i) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.size(width = 64.dp, height = 32.dp), contentAlignment = Alignment.Center) {
                            Icon(icon, null, Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale }, tint = iconColor)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(label, style = MaterialTheme.typography.labelMedium, color = textColor, maxLines = 1)
                    }
                }
            }
        }
    }
}

// =====================================================================
// Библиотека: 4 вкладки
// =====================================================================

@Composable
private fun LibraryScreen(vm: PlayerViewModel, pickFolder: () -> Unit) {
    if (vm.folder == null) {
        EmptyFolderState(pickFolder)
        return
    }
    Column(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = vm.scanning,
            enter = calmEnter(fadeIn() + expandVertically()),
            exit = calmExit(shrinkVertically() + fadeOut()),
        ) {
            if (PowerSave.reduceMotion) {
                // Бесконечная анимация индикатора будит экран на каждом кадре: вместо неё статичная подпись
                Text(
                    stringResource(R.string.scanning_folder),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        // Вкладки — страницы горизонтального пейджера: переход прерывается и перенаправляется на лету,
        // поэтому при быстром переключении страницы не накладываются друг на друга. Работает и свайп.
        val pagerState = rememberPagerState(initialPage = vm.tab) { 4 }
        // pending — страница, к которой мы сами прокручиваем по нажатию на нижнюю панель. Пока она не достигнута,
        // промежуточные остановки пейджера не считаются выбором пользователя (раньше они возвращали вкладку назад
        // и из-за этого переход «залипал»).
        var pending by remember { mutableIntStateOf(-1) }
        val dragging by pagerState.interactionSource.collectIsDraggedAsState()
        LaunchedEffect(dragging) { if (dragging) pending = -1 }
        LaunchedEffect(vm.tab) {
            // Если пейджер уже направляется на эту вкладку (например, при свайпе), анимацию не запускаем
            if (pagerState.targetPage != vm.tab) {
                pending = vm.tab
                if (PowerSave.reduceMotion) pagerState.scrollToPage(vm.tab)
                else pagerState.animateScrollToPage(vm.tab, animationSpec = mtween(300, easing = FastOutSlowInEasing))
            }
        }
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }.collect { page ->
                if (page == pending) pending = -1 else if (pending == -1 && page != vm.tab) vm.openTab(page)
            }
        }
        // При свайпе кнопка нижней панели переключается сразу, как только пейджер выбрал новую страницу,
        // не дожидаясь конца анимации
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.targetPage }.collect { page ->
                if (pending == -1 && page != vm.tab) vm.openTab(page)
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            // Соседние страницы собираются заранее: переход не ждёт отрисовки тяжёлого списка треков
            // В интенсивном режиме страницы заранее не собираются: меньше памяти и работы процессора
            beyondViewportPageCount = PowerSave.pagerBeyondCount(),
            // Энергосбережение: после свайпа страница встаёт на место сразу, без доводки
            flingBehavior = if (PowerSave.reduceMotion) PagerDefaults.flingBehavior(state = pagerState, snapAnimationSpec = snap())
            else PagerDefaults.flingBehavior(state = pagerState),
            key = { it },
        ) { page ->
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when (page) {
                    0 -> PlaylistsTab(vm)
                    1 -> AlbumsTab(vm)
                    2 -> QueuesTab(vm)
                    else -> TracksTab(vm)
                }
            }
        }
    }
}

@Composable
private fun EmptyFolderState(pickFolder: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.FolderOpen, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.choose_folder_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.choose_folder_hint),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = pickFolder) { Text(stringResource(R.string.choose_folder)) }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Переставляет элемент [from] на место элемента [to] (по ключам); используется во время перетаскивания. */
private fun <T> MutableList<T>.moveByKey(from: Any, to: Any, key: (T) -> Any) {
    val a = indexOfFirst { key(it) == from }
    val b = indexOfFirst { key(it) == to }
    if (a < 0 || b < 0 || a == b) return
    add(b, removeAt(a))
}

@Composable
private fun TracksTab(vm: PlayerViewModel) {
    if (vm.tracks.isEmpty()) {
        EmptyHint(if (vm.scanning) stringResource(R.string.scanning_folder) else stringResource(R.string.no_audio_in_folder))
        return
    }
    val query = vm.activeQuery
    val all = vm.sortedTracks
    val sorted = remember(all, query) { if (query.isEmpty()) all else all.filter { it.matches(query) } }
    val working = remember { mutableStateListOf<Track>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reorder = rememberReorderState(
        listState,
        ReorderCallbacks(
            canDrag = { it is String && vm.activeQuery.isEmpty() }, // во время поиска порядок не меняем
            onStart = { working.clear(); working.addAll(sorted) },
            onMove = { from, to -> working.moveByKey(from, to) { it.uri } },
            onEnd = { vm.commitTrackOrder(working.map { it.uri }) },
        ),
    )
    val list: List<Track> = if (reorder.isDragging) working else sorted

    Column(Modifier.fillMaxSize()) {
        SortBar(vm.trackSort, TRACK_SORT_KEYS, { vm.changeTrackSort(it); scope.launch { listState.scrollToItem(0) } })
        if (list.isEmpty()) EmptyHint(stringResource(R.string.nothing_found)) else
        FastScrollContainer(listState) {
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            itemsIndexed(list, key = { _, t -> t.uri }) { i, t ->
                TrackRow(
                    track = t,
                    current = vm.currentId == t.uri,
                    number = i + 1,
                    onClick = { vm.play(list, i) },
                    menu = listOf(
                        MenuKind.ADD_TO_PLAYLIST to { vm.trackForPlaylist = t },
                        MenuKind.ADD_TO_QUEUE to { vm.trackForQueue = t },
                    ),
                    modifier = reorderableItem(reorder, t.uri),
                )
            }
        }
        }
    }
}

@Composable
private fun AlbumsTab(vm: PlayerViewModel) {
    if (vm.albums.isEmpty()) {
        EmptyHint(if (vm.scanning) stringResource(R.string.scanning_folder) else stringResource(R.string.no_albums_yet))
        return
    }
    val query = vm.activeQuery
    val all = vm.sortedAlbums
    val sorted = remember(all, query) { if (query.isEmpty()) all else all.filter { it.matches(query) } }
    val working = remember { mutableStateListOf<Album>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reorder = rememberReorderState(
        listState,
        ReorderCallbacks(
            // «Без альбома» закреплён внизу и не перетаскивается
            canDrag = { it is String && !it.startsWith(NO_ALBUM + "\u0000") && vm.activeQuery.isEmpty() },
            onStart = { working.clear(); working.addAll(sorted) },
            onMove = { from, to -> working.moveByKey(from, to) { it.key } },
            onEnd = { vm.commitAlbumOrder(working.map { it.key }) },
        ),
    )
    val list: List<Album> = if (reorder.isDragging) working else sorted

    Column(Modifier.fillMaxSize()) {
        SortBar(vm.albumSort, ALBUM_SORT_KEYS, { vm.changeAlbumSort(it); scope.launch { listState.scrollToItem(0) } })
        if (list.isEmpty()) EmptyHint(stringResource(R.string.nothing_found)) else
        FastScrollContainer(listState) {
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            itemsIndexed(list, key = { _, a -> a.key }) { _, a ->
                ListItem(
                    modifier = reorderableItem(reorder, a.key).pressableRow({ vm.push(Screen.AlbumScreen(a.key)) }),
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { Cover(a.tracks.firstOrNull()?.uri, 56.dp) },
                    headlineContent = { Text(albumText(a.name), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text("${artistText(a.artist)} · ${tracksCount(a.tracks.size)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                )
            }
        }
        }
    }
}

@Composable
private fun PlaylistsTab(vm: PlayerViewModel) {
    val query = vm.activeQuery
    val all = vm.sortedPlaylists
    val favoritesName = stringResource(R.string.favorites_name)
    val sorted = remember(all, query, favoritesName) {
        if (query.isEmpty()) all else all.filter { it.matches(query, if (it.id == FAVORITES_ID) favoritesName else it.name) }
    }
    val working = remember { mutableStateListOf<Playlist>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reorder = rememberReorderState(
        listState,
        ReorderCallbacks(
            canDrag = { it is String && it != FAVORITES_ID && vm.activeQuery.isEmpty() }, // «Понравившиеся треки» закреплены вверху
            onStart = { working.clear(); working.addAll(sorted) },
            onMove = { from, to -> working.moveByKey(from, to) { it.id } },
            onEnd = { vm.commitPlaylistOrder(working.map { it.id }) },
        ),
    )
    val list: List<Playlist> = if (reorder.isDragging) working else sorted

    Column(Modifier.fillMaxSize()) {
        if (vm.playlists.isNotEmpty()) {
            SortBar(vm.playlistSort, PLAYLIST_SORT_KEYS, { vm.changePlaylistSort(it); scope.launch { listState.scrollToItem(0) } })
        }
        FastScrollContainer(listState, headerCount = 1) {
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            item {
                ListItem(
                    modifier = Modifier.pressableRow({ vm.newPlaylistFirstTrack = null; vm.newPlaylistDialog = true }),
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) },
                    headlineContent = { Text(stringResource(R.string.new_playlist), color = MaterialTheme.colorScheme.primary) },
                )
                HorizontalDivider()
            }
            if (vm.playlists.isEmpty()) {
                item { Text(stringResource(R.string.no_playlists_yet), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else if (list.isEmpty()) {
                item { Text(stringResource(R.string.nothing_found), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            itemsIndexed(list, key = { _, p -> p.id }) { _, p ->
                val n = vm.playlistTracks(p).size
                ListItem(
                    modifier = reorderableItem(reorder, p.id).pressableRow({ vm.push(Screen.PlaylistScreen(p.id)) }),
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        if (p.id == FAVORITES_ID) Icon(Icons.Default.Favorite, null, tint = MaterialTheme.colorScheme.primary)
                        else Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null)
                    },
                    headlineContent = { Text(p.label(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(tracksCount(n)) },
                    trailingContent = {
                        // Плейлист по умолчанию нельзя переименовать или удалить
                        if (p.id != FAVORITES_ID) {
                            RowMenu(
                                listOf(
                                    MenuKind.RENAME to { vm.renameTarget = p },
                                    MenuKind.DELETE to { vm.deletePlaylist(p.id) },
                                ),
                            )
                        }
                    },
                )
            }
        }
        }
    }
}

// =====================================================================
// Раздел «Очереди»
// =====================================================================

/**
 * Раздел «Очереди»: сверху компактный переключатель между четырьмя очередями (как нижняя панель, только меньше),
 * ниже — треки выбранной очереди. Треки можно переставлять (долгое нажатие или пункты меню), убирать и запускать.
 */
@Composable
private fun QueuesTab(vm: PlayerViewModel) {
    Column(Modifier.fillMaxSize()) {
        QueueSwitcher(vm)
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        // Смена очереди — такой же горизонтальный слайд, как между разделами, только вдвое быстрее.
        // Направление зависит от номера очереди; у каждой очереди своё состояние списка.
        AnimatedContent(
            targetState = vm.selectedQueue,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                val spec = mtween<IntOffset>(QUEUE_SWITCH_MS, easing = FastOutSlowInEasing)
                (slideInHorizontally(spec) { it * dir }) togetherWith (slideOutHorizontally(spec) { -it * dir })
            },
            modifier = Modifier.fillMaxSize().clipToBounds(),
            label = "queueSwitch",
        ) { queueId ->
            // Непрозрачный фон: уходящий и входящий списки не просвечивают друг через друга
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                QueueList(vm, queueId)
            }
        }
    }
}

private const val QUEUE_SWITCH_MS = 150

@Composable
private fun QueueList(vm: PlayerViewModel, queueId: Int) {
    val queue = vm.queues.getOrNull(queueId) ?: return
    val query = vm.activeQuery
    val all = vm.queueTracks(queue)
    val shown = remember(all, query) { if (query.isEmpty()) all else all.filter { it.matches(query) } }
    val searching = query.isNotEmpty()
    TrackListScreen(
        vm = vm,
        header = "${queue.label()} · ${tracksCount(all.size)}",
        list = shown,
        emptyText = if (searching) stringResource(R.string.nothing_found) else stringResource(R.string.queue_empty, queue.label()),
        menuFor = { t ->
            val i = shown.indexOfFirst { it.uri == t.uri }
            buildList {
                // Во время поиска порядок не меняем (как и при перетаскивании)
                if (!searching && i > 0) add(MenuKind.MOVE_UP to { vm.moveInQueue(queue.id, t, -1) })
                if (!searching && i in 0 until shown.lastIndex) add(MenuKind.MOVE_DOWN to { vm.moveInQueue(queue.id, t, 1) })
                add(MenuKind.REMOVE_FROM_QUEUE to { vm.removeFromQueue(queue.id, t) })
            }
        },
        sourceKey = "queue:${queue.id}",
        onReorder = if (searching) null else ({ uris -> vm.reorderQueueTracks(queue.id, uris) }),
    )
}

/** Компактный переключатель очередей: капсула с четырьмя сегментами и «плашкой» выбранной очереди, которая переезжает на пружине. */
@Composable
private fun QueueSwitcher(vm: PlayerViewModel) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainerHigh),
        ) {
            val segW = maxWidth / QUEUE_COUNT
            val pillX by animateDpAsState(
                segW * vm.selectedQueue,
                mspring(dampingRatio = 0.75f, stiffness = 480f),
                label = "queuePill",
            )
            Box(
                Modifier
                    .offset(x = pillX)
                    .padding(3.dp)
                    .size(width = segW - 6.dp, height = 30.dp)
                    .background(cs.secondaryContainer, RoundedCornerShape(15.dp)),
            )
            Row(Modifier.fillMaxSize()) {
                vm.queues.forEach { q ->
                    val selected = vm.selectedQueue == q.id
                    val textColor by animateColorAsState(
                        if (selected) cs.onSecondaryContainer else cs.onSurfaceVariant, mtween(220), label = "queueText",
                    )
                    val src = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pressScale(src, 0.95f)
                            .clickable(interactionSource = src, indication = null, role = Role.Tab) {
                                if (!selected) {
                                    vm.selectQueue(q.id)
                                    vm.searchQuery = "" // у каждой очереди свой список: старый запрос к новой не относится
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            q.label(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = textColor,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

// =====================================================================
// Экраны альбома и плейлиста
// =====================================================================

@Composable
private fun AlbumScreen(vm: PlayerViewModel, key: String) {
    val album = vm.albums.find { it.key == key }
    if (album == null) { EmptyHint(stringResource(R.string.album_not_found)); return }
    TrackListScreen(
        vm = vm,
        header = "${artistText(album.artist)} · ${tracksCount(album.tracks.size)}",
        list = album.tracks,
        showCovers = false, // внутри альбома обложка у всех треков одна: показываем её один раз сверху
        coverUri = album.tracks.firstOrNull()?.uri,
        menuFor = { t ->
            listOf(
                MenuKind.ADD_TO_PLAYLIST to { vm.trackForPlaylist = t },
                MenuKind.ADD_TO_QUEUE to { vm.trackForQueue = t },
            )
        },
        sourceKey = "album:$key",
    )
}

@Composable
private fun PlaylistScreen(vm: PlayerViewModel, id: String) {
    val pl = vm.playlists.find { it.id == id }
    if (pl == null) { EmptyHint(stringResource(R.string.playlist_not_found)); return }
    val list = vm.playlistTracks(pl).distinctBy { it.uri }
    TrackListScreen(
        vm = vm,
        header = tracksCount(list.size),
        list = list,
        emptyText = stringResource(R.string.playlist_empty),
        menuFor = { t ->
            listOf(
                MenuKind.ADD_TO_QUEUE to { vm.trackForQueue = t },
                MenuKind.REMOVE_FROM_PLAYLIST to { vm.removeFromPlaylist(pl.id, t) },
            )
        },
        sourceKey = "playlist:$id",
        onReorder = { uris -> vm.reorderPlaylistTracks(pl.id, uris) },
    )
}

@Composable
private fun TrackListScreen(
    vm: PlayerViewModel,
    header: String,
    list: List<Track>,
    emptyText: String = stringResource(R.string.no_tracks),
    showCovers: Boolean = true,
    /** Если задан, над заголовком показывается большая обложка (экран альбома). */
    coverUri: String? = null,
    menuFor: (Track) -> List<Pair<MenuKind, () -> Unit>>,
    /** Ключ списка (альбом/плейлист): по нему кнопка «Играть» узнаёт, что очередь запущена именно отсюда. */
    sourceKey: String,
    /** Если задан, треки можно перетаскивать (долгое нажатие); получает новый порядок URI. */
    onReorder: ((List<String>) -> Unit)? = null,
) {
    val working = remember { mutableStateListOf<Track>() }
    val listState = rememberLazyListState()
    val reorder = rememberReorderState(
        listState,
        ReorderCallbacks(
            canDrag = { onReorder != null && it is String },
            onStart = { working.clear(); working.addAll(list) },
            onMove = { from, to -> working.moveByKey(from, to) { it.uri } },
            onEnd = { onReorder?.invoke(working.map { it.uri }) },
        ),
    )
    val shown: List<Track> = if (reorder.isDragging) working else list

    FastScrollContainer(listState, headerCount = 1) {
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        item {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = if (coverUri != null) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                if (coverUri != null) {
                    Cover(coverUri, ALBUM_COVER_SIZE)
                    Spacer(Modifier.height(16.dp))
                }
                Text(header, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Пока очередь играет из этого списка, кнопка превращается в «Пауза» и ставит воспроизведение на паузу
                    val listPlaying = vm.playingSource == sourceKey && vm.currentId != null && vm.uiPlaying
                    ActionPill(
                        stringResource(if (listPlaying) R.string.pause else R.string.play),
                        if (listPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        primary = true, enabled = list.isNotEmpty(),
                        onClick = { vm.toggleList(list, sourceKey) },
                    )
                    ActionPill(
                        stringResource(R.string.shuffle_list), Icons.Default.Shuffle, primary = false, enabled = list.isNotEmpty(),
                        onClick = { vm.playShuffled(list, sourceKey) }, spin = true,
                    )
                }
            }
        }
        if (list.isEmpty()) item { Text(emptyText, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        itemsIndexed(shown, key = { _, t -> t.uri }) { i, t ->
            TrackRow(
                t, vm.currentId == t.uri, i + 1, { vm.play(shown, i, source = sourceKey) }, menuFor(t),
                if (onReorder != null) reorderableItem(reorder, t.uri) else Modifier.animateItem(
                    fadeInSpec = null,
                    fadeOutSpec = null,
                    placementSpec = if (FastScroll.active || PowerSave.reduceMotion) null else mspring(
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                        visibilityThreshold = androidx.compose.ui.unit.IntOffset.Companion.VisibilityThreshold,
                    ),
                ),
                showCover = showCovers,
            )
        }
    }
    }
}

// =====================================================================
// Общие элементы
// =====================================================================

@Composable
private fun TrackRow(
    track: Track,
    current: Boolean,
    number: Int,
    onClick: () -> Unit,
    menu: List<Pair<MenuKind, () -> Unit>>,
    modifier: Modifier = Modifier,
    showCover: Boolean = true,
) {
    val color by animateColorAsState(
        if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        mtween(300), label = "trackColor",
    )
    ListItem(
        modifier = modifier.pressableRow(onClick, highlighted = current),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Порядковый номер (до 4–5 цифр влезает без переноса); у играющего трека вместо него треугольник
                Box(Modifier.width(TRACK_NUMBER_WIDTH), contentAlignment = Alignment.Center) {
                    if (current) {
                        Icon(Icons.Default.PlayArrow, stringResource(R.string.now_playing), Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                    } else {
                        Text(
                            number.toString(),
                            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                if (showCover) {
                    Spacer(Modifier.width(8.dp))
                    Cover(track.uri, TRACK_COVER_SIZE)
                }
            }
        },
        headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = color) },
        supportingContent = { Text(artistText(track.artist), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.durationMs > 0) Text(formatTime(track.durationMs), style = MaterialTheme.typography.bodySmall)
                RowMenu(menu)
            }
        },
    )
}

@Composable
private fun RowMenu(items: List<Pair<MenuKind, () -> Unit>>) = AppMenu(items)

/** Размер обложки в списках треков (меньше, чем 56 dp в «Альбомах»). */
private val TRACK_COVER_SIZE = 44.dp
private val TRACK_NUMBER_WIDTH = 36.dp

/** Размер обложки на экране альбома: чуть меньше, чем в плеере. */
private val ALBUM_COVER_SIZE = 200.dp

/** Размер обложки в полноэкранном плеере. Мини-плеер декодирует обложку в том же размере, чтобы при открытии плеера она уже была в кэше. */
private val PLAYER_COVER_SIZE = 280.dp

/** Высота всего, что в плеере находится под обложкой (плашка, название, полоса прогресса, кнопки, панель звука) с запасом. Остальное отдаётся обложке. */
private val PLAYER_CONTENT_RESERVE = 400.dp

/** Не больше двух одновременных декодирований обложек, чтобы прокрутка списка не забивала процессор. */
private val coverGate = Semaphore(2)

/** Кэш обложек в памяти; поверх него работает дисковый кэш [CoverStore] (переживает перезапуск приложения). */
private val coverCache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}

/** Интенсивный режим: кэш обложек в памяти втрое меньше (меньше живых картинок в куче, реже работа сборщика мусора). */
internal fun applyCoverCachePolicy() {
    coverCache.resize(if (PowerSave.intensiveNow) 8 * 1024 * 1024 else 24 * 1024 * 1024)
}

@Composable
private fun Cover(uri: String?, size: Dp, decodeSize: Dp = size) {
    val ctx = LocalContext.current
    val targetPx = with(LocalDensity.current) { decodeSize.roundToPx() }
    val key = "$uri@${CoverStore.tierPx(targetPx)}"
    val fastScrolling = FastScroll.active
    val bmp by produceState<ImageBitmap?>(if (uri == null) null else coverCache.get(key), key, fastScrolling) {
        if (uri == null) {
            value = null
            return@produceState
        }
        val cached = coverCache.get(key)
        if (cached != null) {
            value = cached
            return@produceState
        }
        // Пока тянут ползунок, новые обложки не грузим: иначе список дёргается и моргает
        if (fastScrolling) return@produceState
        value = withContext(Dispatchers.IO) { coverGate.withPermit { CoverStore.load(ctx, uri, targetPx) } }
            ?.also { coverCache.put(key, it) }
    }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = bmp, animationSpec = mtween(if (fastScrolling) 0 else 200), label = "cover") { b ->
            if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Окно выбора плейлистов для трека (сердечко в плеере работает как «плюс» в Spotify; то же окно открывает пункт «Добавить в плейлист»).
 * Нажатие на плейлист добавляет трек или, если он там уже есть, убирает. Галочка показывает, в каких плейлистах трек уже лежит.
 * Сверху, вне прокрутки, закреплены «Понравившиеся треки» с разделителем; ниже — остальные плейлисты в том же порядке, что и в разделе «Плейлисты».
 */
@Composable
private fun AddToPlaylistDialog(vm: PlayerViewModel, track: Track) {
    // Порядок фиксируем при открытии: иначе при сортировке «по дате изменения» строки прыгали бы после каждого нажатия
    val order = remember { vm.sortedPlaylists.map { it.id } }
    val byId = vm.playlists.associateBy { it.id }
    val favorites = byId[FAVORITES_ID]
    val others = order.filter { it != FAVORITES_ID }.mapNotNull { byId[it] }
    val close = { vm.trackForPlaylist = null }

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 560.dp).fillMaxWidth(),
        ) {
            Column(Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.75f).dp)) {
                Text(
                    stringResource(R.string.menu_add_to_playlist),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                )
                if (favorites != null) {
                    PlaylistPickRow(vm, favorites, track, favorite = true)
                    HorizontalDivider()
                }
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(others, key = { it.id }) { p -> PlaylistPickRow(vm, p, track, favorite = false) }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        vm.newPlaylistFirstTrack = track
                        vm.newPlaylistDialog = true
                        close()
                    }) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.new_playlist))
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = close) { Text(stringResource(R.string.done)) }
                }
            }
        }
    }
}

/** Компактная строка плейлиста в окне выбора: название, число треков и галочка, если трек уже в этом плейлисте. */
@Composable
private fun PlaylistPickRow(vm: PlayerViewModel, p: Playlist, track: Track, favorite: Boolean) {
    val n = vm.playlistTracks(p).size
    val contains = track.uri in p.trackUris
    Row(
        Modifier.fillMaxWidth().clickable { vm.togglePlaylistMembership(p.id, track) }
            .heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (favorite) Icon(Icons.Default.Favorite, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        else Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(p.label(), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                tracksCount(n),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(Modifier.size(20.dp)) {
            if (contains) Icon(Icons.Default.Check, stringResource(R.string.cd_in_playlist), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * Окно выбора очереди для трека (кнопка очередей в плеере и пункт «Добавить в очередь» в меню «⋮»).
 * По виду то же, что и окно плейлистов, но строки крупнее: очередей всегда четыре. Нажатие добавляет трек в конец очереди
 * или, если он там уже есть, убирает; галочка показывает, в каких очередях трек уже лежит.
 */
@Composable
private fun AddToQueueDialog(vm: PlayerViewModel, track: Track) {
    val close = { vm.trackForQueue = null }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 560.dp).fillMaxWidth(),
        ) {
            Column {
                Text(
                    stringResource(R.string.menu_add_to_queue),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                )
                vm.queues.forEach { q -> QueuePickRow(vm, q, track) }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = close) { Text(stringResource(R.string.done)) }
                }
            }
        }
    }
}

/** Просторная строка очереди в окне выбора: название, число треков и галочка, если трек уже в этой очереди. */
@Composable
private fun QueuePickRow(vm: PlayerViewModel, q: PlayQueue, track: Track) {
    val n = vm.queueTracks(q).size
    val contains = track.uri in q.trackUris
    Row(
        Modifier.fillMaxWidth().clickable { vm.toggleQueueMembership(q.id, track) }
            .heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.QueueMusic, null, Modifier.size(24.dp),
            tint = if (contains) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(q.label(), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                tracksCount(n),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(Modifier.size(24.dp)) {
            if (contains) Icon(Icons.Default.Check, stringResource(R.string.cd_in_queue), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, label = { Text(stringResource(R.string.name_label)) }) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// =====================================================================
// Мини-плеер и полный плеер
// =====================================================================

@Composable
private fun MiniPlayer(vm: PlayerViewModel, onClick: () -> Unit = { vm.openPlayer() }, showProgress: Boolean = false) {
    val t = vm.currentTrack
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column {
            if (showProgress) MiniProgress(vm, t?.durationMs ?: 0L)
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(t?.uri, 44.dp, decodeSize = PLAYER_COVER_SIZE)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(t?.title ?: "…", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text(artistText(t?.artist.orEmpty()), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
                PlayPauseButton(vm.uiPlaying, { vm.togglePlay() }, buttonSize = 40.dp, iconSize = 20.dp)
                Spacer(Modifier.width(4.dp))
                SkipButton(Icons.Default.SkipNext, stringResource(R.string.next), 1, { vm.next() }, boxSize = 44.dp, iconSize = 26.dp)
            }
        }
    }
}

/** Полноэкранный плеер. Рисуется отдельным слоем поверх приложения, поэтому у него своя шапка и свои отступы под системные панели. */
@Composable
private fun PlayerScreen(vm: PlayerViewModel) {
    val t = vm.currentTrack
    var showInfo by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.pop() }) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.cd_collapse))
                }
                Text(
                    stringResource(R.string.now_playing),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                // «О треке» стоит в правом верхнем углу, на одной линии с надписью «Сейчас играет»
                TrackInfoButton(enabled = t != null) { showInfo = true }
            }
            // Кнопка «Текст песни» стоит сразу под кнопкой «i»; строку поднимаем, чтобы между кнопками не было пустоты
            Row(Modifier.fillMaxWidth().height(40.dp).offset(y = (-8).dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                LyricsButton(enabled = t != null) { vm.openLyrics() }
            }
            // Обложка растёт на высоких экранах, чтобы плеер занимал всю высоту без пустой полосы внизу.
            // На низких экранах содержимое прокручивается: панель звука не помещается рядом с обложкой.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val lo = minOf(PLAYER_COVER_SIZE, maxWidth)
                val hi = maxOf(lo, maxWidth)
                val coverSize = minOf(maxOf(maxHeight - PLAYER_CONTENT_RESERVE, lo), hi)
                Column(
                    Modifier.fillMaxWidth().heightIn(min = maxHeight).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    QualityBadge(t?.info)
                    Spacer(Modifier.height(12.dp))
                    Cover(t?.uri, coverSize)
                    Spacer(Modifier.height(24.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        QueueButton(inQueue = t != null && t.uri in vm.queuedUris, enabled = t != null) {
                            t?.let { vm.trackForQueue = it }
                        }
                        AnimatedContent(
                            targetState = t,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            transitionSpec = { fadeIn(mtween(250)) togetherWith fadeOut(mtween(150)) },
                            label = "nowPlayingTitle",
                        ) { tr ->
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    tr?.title ?: "", style = MaterialTheme.typography.titleLarge,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                                )
                                Text(
                                    artistText(tr?.artist.orEmpty()), style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        SaveToPlaylistButton(saved = t != null && t.uri in vm.savedUris, trackKey = t?.uri, enabled = t != null) {
                            t?.let { vm.openAddToPlaylist(it) }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    SeekBar(vm, t?.durationMs ?: 0L)
                    Spacer(Modifier.height(8.dp))
                    PlayerControls(vm)
                    Spacer(Modifier.height(16.dp))
                    SoundPanel(vm)
                }
            }
        }
    }

    if (showInfo && t != null) TrackInfoDialog(vm, t) { showInfo = false }
}

/**
 * Кнопка очередей слева от названия: открывает окно выбора очереди (всего их четыре), в которую добавляется трек.
 * Значок закрашен цветом темы, если трек уже лежит хотя бы в одной очереди.
 */
@Composable
private fun QueueButton(inQueue: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val tint = if (inQueue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.QueueMusic,
            contentDescription = stringResource(if (inQueue) R.string.cd_edit_queues else R.string.menu_add_to_queue),
            tint = tint,
        )
    }
}

/** Круглая кнопка «i» в правом верхнем углу плеера: открывает окно со всеми данными о треке. */
@Composable
private fun TrackInfoButton(enabled: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(26.dp).border(1.5.dp, c, CircleShape), contentAlignment = Alignment.Center) {
            Text(
                "i", color = c, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic,
            )
        }
    }
}

/** Сердечко справа от названия (как «плюс» в Spotify): открывает окно выбора плейлистов. Закрашено, если трек уже есть хотя бы в одном плейлисте. */
@Composable
private fun SaveToPlaylistButton(saved: Boolean, trackKey: Any?, enabled: Boolean, onClick: () -> Unit) {
    AnimatedHeart(
        saved = saved, trackKey = trackKey, enabled = enabled,
        description = stringResource(if (saved) R.string.cd_edit_playlists else R.string.menu_add_to_playlist),
        onClick = onClick,
    )
}

/** Окно «О треке»: теги и свойства файла (без пути к папке). */
@Composable
private fun TrackInfoDialog(vm: PlayerViewModel, track: Track, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val channels = remember(track.uri) { vm.currentChannelCount() }
    val sections by produceState<List<TrackDetails.Section>?>(null, track.uri) {
        value = TrackDetails.load(ctx, track, channels)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.track_info_title)) },
        text = {
            val list = sections
            if (list == null) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    list.forEachIndexed { si, section ->
                        if (si > 0) Spacer(Modifier.height(12.dp))
                        Text(section.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        section.rows.forEach { (label, value) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(
                                    label, Modifier.weight(0.42f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(value, Modifier.weight(0.58f), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

/**
 * Скруглённая плашка над обложкой: формат и качество играющего трека («MP3 / 192 kbps», «FLAC / 24 Bit – 96 kHz»).
 * Цвет зависит от «редкости» качества (см. [QualityTier]). Высота зарезервирована, чтобы обложка не прыгала при смене трека.
 */
@Composable
private fun QualityBadge(info: AudioInfo?) {
    Box(Modifier.height(28.dp), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = info?.takeIf { it.format.isNotEmpty() },
            transitionSpec = { fadeIn(mtween(250)) togetherWith fadeOut(mtween(150)) },
            label = "qualityBadge",
        ) { q ->
            if (q != null) {
                val tier = q.tier()
                Text(
                    q.label(),
                    color = tier.fg,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier
                        .background(Brush.horizontalGradient(listOf(tier.from, tier.to)), RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                )
            }
        }
    }
}

/**
 * Быстрые настройки звука прямо в плеере: ряд кнопок «Bass», «Treble», «Subsonic», «EQ», «Speed».
 * Нажатие на «Bass», «Treble», «Subsonic» и «Speed» открывает окно с тем же переключателем и ползунком, что и в настройках;
 * «EQ» открывает эквалайзер. Включённые эффекты подсвечены акцентным цветом.
 */
@Composable
private fun SoundPanel(vm: PlayerViewModel) {
    var dialog by remember { mutableStateOf<Int?>(null) }
    val lk = PowerSave.intensive // интенсивный режим: все кнопки заблокированы
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SoundButton("Bass", Icons.Default.Speaker, vm.bassEnabled && !lk, Modifier.weight(1f), lk) { dialog = 0 }
        SoundButton("Treble", Icons.Default.GraphicEq, vm.trebleEnabled && !lk, Modifier.weight(1f), lk) { dialog = 1 }
        SoundButton("Subsonic", Icons.Default.Waves, vm.subEnabled && !lk, Modifier.weight(1f), lk) { dialog = 2 }
        SoundButton("EQ", Icons.Default.Equalizer, vm.eqMode != EqMode.OFF && !lk, Modifier.weight(1f), lk) { vm.openPlayerEqualizer() }
        // Включённая скорость показывается прямо на плитке («1,5x»)
        SoundButton(
            if (vm.speedEnabled && !lk) speedLabel(vm.speedValue) else "Speed",
            Icons.Default.Speed, vm.speedEnabled && !lk, Modifier.weight(1f), lk,
        ) { dialog = 3 }
    }
    dialog?.let { d ->
        AlertDialog(
            onDismissRequest = { dialog = null },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.done)) } },
            text = {
                when (d) {
                    0 -> BassControl(vm)
                    1 -> TrebleControl(vm)
                    2 -> SubsonicControl(vm)
                    else -> SpeedControl(vm)
                }
            },
        )
    }
}

/**
 * Кнопка быстрой настройки звука в минималистичном стиле: без фона и рамки, только значок и подпись.
 * Выключенная приглушена, включённая окрашена в акцентный цвет темы и получает маленькую точку снизу.
 */
@Composable
private fun SoundButton(label: String, icon: ImageVector, active: Boolean, modifier: Modifier, locked: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val fg by animateColorAsState(
        if (active) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.6f),
        mtween(220), label = "soundTileFg",
    )
    val dot by animateColorAsState(
        if (active) cs.primary else Color.Transparent,
        mtween(220), label = "soundTileDot",
    )
    val source = remember { MutableInteractionSource() }
    Column(
        modifier
            .pressScale(source, pressedScale = 0.92f)
            .graphicsLayer { alpha = if (locked) 0.38f else 1f }
            .clickable(interactionSource = source, indication = null, enabled = !locked, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
        Spacer(Modifier.height(5.dp))
        Box(Modifier.size(4.dp).background(dot, CircleShape))
    }
}

/** Общий вид настройки эффекта: заголовок, подпись, переключатель и ползунок. Одинаков для басов, требла и subsonic-фильтра. */
@Composable
private fun FxControl(
    title: String,
    enabled: Boolean,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onToggle: (Boolean) -> Unit,
    onValue: (Float) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locked = PowerSave.intensive
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    when {
                        locked -> stringResource(R.string.locked_by_intensive)
                        enabled -> valueText
                        else -> stringResource(R.string.fx_off)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AppSwitch(checked = enabled && !locked, onCheckedChange = onToggle, enabled = !locked)
        }
        Slider(
            value = value,
            onValueChange = onValue,
            onValueChangeFinished = onFinish,
            valueRange = range,
            steps = steps,
            enabled = enabled && !locked,
        )
    }
}

/** Усиление басов: переключатель и ползунок 0...+15 дБ. Работает независимо от эквалайзера. */
@Composable
private fun BassControl(vm: PlayerViewModel, modifier: Modifier = Modifier) = FxControl(
    title = stringResource(R.string.bass_title),
    enabled = vm.bassEnabled,
    valueText = stringResource(R.string.fmt_plus_db, vm.bassDb.roundToInt()),
    value = vm.bassDb,
    range = 0f..AudioFx.BASS_MAX_DB,
    steps = AudioFx.BASS_MAX_DB.toInt() - 1,
    onToggle = { vm.changeBassEnabled(it) },
    onValue = { vm.changeBassDb(it) },
    onFinish = { vm.commitSound() },
    modifier = modifier,
)

/** Усиление требла (высоких частот): переключатель и ползунок 0...+15 дБ. */
@Composable
private fun TrebleControl(vm: PlayerViewModel, modifier: Modifier = Modifier) = FxControl(
    title = stringResource(R.string.treble_title),
    enabled = vm.trebleEnabled,
    valueText = stringResource(R.string.fmt_plus_db, vm.trebleDb.roundToInt()),
    value = vm.trebleDb,
    range = 0f..AudioFx.TREBLE_MAX_DB,
    steps = AudioFx.TREBLE_MAX_DB.toInt() - 1,
    onToggle = { vm.changeTrebleEnabled(it) },
    onValue = { vm.changeTrebleDb(it) },
    onFinish = { vm.commitSound() },
    modifier = modifier,
)

/**
 * Скорость воспроизведения: переключатель и ползунок от −3x (в 3 раза медленнее) до 3x (в 3 раза быстрее)
 * с шагом 0,1, плюс кнопки точной подстройки.
 */
@Composable
private fun SpeedControl(vm: PlayerViewModel, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        FxControl(
            title = stringResource(R.string.speed_title),
            enabled = vm.speedEnabled,
            valueText = speedLabel(vm.speedValue) +
                when {
                    vm.speedValue > 0f -> " · " + stringResource(R.string.speed_faster_word)
                    vm.speedValue < 0f -> " · " + stringResource(R.string.speed_slower_word)
                    else -> " · " + stringResource(R.string.speed_normal_word)
                },
            value = vm.speedValue,
            range = AudioFx.SPEED_MIN..AudioFx.SPEED_MAX,
            steps = 39,
            onToggle = { vm.changeSpeedEnabled(it) },
            onValue = { vm.changeSpeedValue(it) },
            onFinish = { vm.commitSound() },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { vm.changeSpeedValue(vm.speedValue - 0.1f); vm.commitSound() },
                enabled = !PowerSave.intensive && vm.speedEnabled && vm.speedValue > AudioFx.SPEED_MIN + 0.05f,
            ) { Text(stringResource(R.string.speed_slower)) }
            TextButton(
                onClick = { vm.changeSpeedValue(0f); vm.commitSound() },
                enabled = !PowerSave.intensive && vm.speedEnabled && vm.speedValue != 0f,
            ) { Text("1x") }
            TextButton(
                onClick = { vm.changeSpeedValue(vm.speedValue + 0.1f); vm.commitSound() },
                enabled = !PowerSave.intensive && vm.speedEnabled && vm.speedValue < AudioFx.SPEED_MAX - 0.05f,
            ) { Text(stringResource(R.string.speed_faster)) }
        }
    }
}

/** «1,5x» — в 1,5 раза быстрее, «−1,5x» — в 1,5 раза медленнее. */
private fun speedLabel(pos: Float): String {
    val v = AudioFx.speedLabelValue(pos)
    val num = "%.1f".format(kotlin.math.abs(v))
    return if (v < 0f) "−${num}x" else "${num}x"
}

/** Subsonic-фильтр (срез инфранизких частот): переключатель и ползунок интенсивности 0...100 %. */
@Composable
private fun SubsonicControl(vm: PlayerViewModel, modifier: Modifier = Modifier) = FxControl(
    title = stringResource(R.string.subsonic_title),
    enabled = vm.subEnabled,
    valueText = "${vm.subLevel.roundToInt()} %",
    value = vm.subLevel,
    range = 0f..AudioFx.SUB_MAX,
    steps = 9,
    onToggle = { vm.changeSubEnabled(it) },
    onValue = { vm.changeSubLevel(it) },
    onFinish = { vm.commitSound() },
    modifier = modifier,
)

@Composable
private fun eqModeLabel(mode: EqMode): String = stringResource(
    when (mode) {
        EqMode.OFF -> R.string.eqmode_off
        EqMode.GLOBAL -> R.string.eqmode_global
        EqMode.PER_TRACK -> R.string.eqmode_per_track
    },
)

/**
 * Полоса прогресса. Позиция читается на каждом кадре, но рисуется прямо в фазе отрисовки (drawBehind):
 * состояние читается внутри draw-блока, поэтому покадровые обновления вообще не вызывают перекомпозицию.
 * Перекомпонуется только подпись со временем, и то раз в секунду.
 */
@Composable
private fun SeekBar(vm: PlayerViewModel, durationMs: Long) {
    val posState = remember { mutableLongStateOf(0L) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val total = durationMs.coerceAtLeast(1L)

    LaunchedEffect(vm.currentId, vm.isPlaying) {
        posState.longValue = vm.controller?.currentPosition ?: 0L
        if (vm.isPlaying) {
            while (true) {
                // Обычно позиция читается на каждом кадре; в режимах энергосбережения — редко (раз в 0,25 или 1 с)
                val tick = PowerSave.progressTickMs()
                if (tick == 0L) withFrameNanos { } else delay(tick)
                if (dragFraction == null) {
                    posState.longValue = vm.controller?.currentPosition ?: posState.longValue
                }
            }
        }
    }

    val elapsedSec by remember(total) {
        derivedStateOf { (dragFraction?.let { (it * total).toLong() } ?: posState.longValue) / 1000 }
    }
    val totalText = remember(durationMs) { formatTime(durationMs) }
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.surfaceVariant

    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .height(36.dp)
                .pointerInput(total) {
                    detectTapGestures { offset ->
                        val target = ((offset.x / size.width).coerceIn(0f, 1f) * total).toLong()
                        posState.longValue = target
                        vm.seekTo(target)
                    }
                }
                .pointerInput(total) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> dragFraction = (offset.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            dragFraction?.let { f ->
                                val target = (f * total).toLong()
                                posState.longValue = target
                                vm.seekTo(target)
                            }
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null },
                    ) { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                }
                .drawBehind {
                    val f = dragFraction ?: (posState.longValue.toFloat() / total).coerceIn(0f, 1f)
                    val trackH = 4.dp.toPx()
                    val cy = size.height / 2
                    val corner = CornerRadius(trackH / 2)
                    drawRoundRect(
                        inactive, topLeft = Offset(0f, cy - trackH / 2),
                        size = Size(size.width, trackH), cornerRadius = corner,
                    )
                    drawRoundRect(
                        active, topLeft = Offset(0f, cy - trackH / 2),
                        size = Size(size.width * f, trackH), cornerRadius = corner,
                    )
                    drawCircle(active, radius = 8.dp.toPx(), center = Offset(size.width * f, cy))
                },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(elapsedSec * 1000), style = MaterialTheme.typography.bodySmall)
            Text(totalText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PlayerControls(vm: PlayerViewModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        ToggleIconButton(active = vm.shuffle, stateKey = vm.shuffle, onClick = { vm.toggleShuffle() }) { tint ->
            Icon(Icons.Default.Shuffle, stringResource(R.string.cd_shuffle), tint = tint)
        }
        SkipButton(Icons.Default.SkipPrevious, stringResource(R.string.previous), -1, { vm.prev() })
        PlayPauseButton(vm.uiPlaying, { vm.togglePlay() }, buttonSize = 72.dp, iconSize = 34.dp)
        SkipButton(Icons.Default.SkipNext, stringResource(R.string.next), 1, { vm.next() })
        ToggleIconButton(active = vm.repeat != Player.REPEAT_MODE_OFF, stateKey = vm.repeat, onClick = { vm.cycleRepeat() }) { tint ->
            AnimatedContent(
                targetState = vm.repeat == Player.REPEAT_MODE_ONE,
                transitionSpec = {
                    (scaleIn(mspring(dampingRatio = 0.6f, stiffness = 500f)) + fadeIn(mtween(120))) togetherWith
                        (scaleOut(mtween(100)) + fadeOut(mtween(100)))
                },
                label = "repeatIcon",
            ) { one ->
                Icon(if (one) Icons.Default.RepeatOne else Icons.Default.Repeat, stringResource(R.string.cd_repeat), tint = tint)
            }
        }
    }
}

// =====================================================================
// Настройки
// =====================================================================

@Composable
private fun SettingsScreen(vm: PlayerViewModel, pickFolder: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // ----- Папка -----
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.music_folder), style = MaterialTheme.typography.titleMedium)
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        vm.folder?.let { folderLabel(it) } ?: stringResource(R.string.folder_not_selected),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (vm.folder != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (vm.scanning) stringResource(R.string.scanning) else stringResource(R.string.tracks_found, vm.tracks.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Button(onClick = pickFolder, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.choose_folder))
            }
            // Одна кнопка. В интенсивном режиме это единственный способ обновить список (быстрое сканирование),
            // в остальных режимах — полное пересканирование, как и раньше.
            FilledTonalButton(
                onClick = { vm.rescan(force = !PowerSave.intensive) },
                enabled = vm.folder != null && !vm.scanning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp))
                Text(stringResource(if (PowerSave.intensive) R.string.scan_folder else R.string.rescan_folder))
            }
            Text(
                stringResource(if (PowerSave.intensive) R.string.scan_hint_intensive else R.string.scan_hint_normal),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider()

        // ----- Тема -----
        Text(
            stringResource(R.string.theme),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        val themeModes = ThemeMode.entries
        val themeLocked = PowerSave.intensive
        SlidingSelector(
            labels = themeModes.map { stringResource(it.label) },
            icons = listOf(Icons.Default.LightMode, Icons.Default.DarkMode, Icons.Default.SettingsBrightness),
            selected = themeModes.indexOf(if (themeLocked) ThemeMode.DARK else vm.themeMode),
            onSelect = { vm.changeTheme(themeModes[it]) },
            modifier = Modifier.padding(horizontal = 16.dp),
            enabled = !themeLocked,
        )
        SelectorCaption(
            stringResource(if (themeLocked) R.string.theme_locked_caption else vm.themeMode.description),
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()

        // ----- Энергоэффективность -----
        Text(
            stringResource(R.string.power_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        val powerModes = PowerMode.entries
        SlidingSelector(
            labels = powerModes.map { stringResource(it.label) },
            icons = listOf(Icons.Default.BatteryFull, Icons.Default.BatteryStd, Icons.Default.BatteryAlert),
            selected = powerModes.indexOf(vm.powerMode),
            onSelect = { vm.changePowerMode(powerModes[it]) },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        SelectorCaption(stringResource(vm.powerMode.description))
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()

        // ----- Звук -----
        Text(
            stringResource(R.string.sound_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        if (PowerSave.intensive) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text(
                    stringResource(R.string.intensive_sound_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
        SwitchRow(
            title = stringResource(R.string.mono),
            subtitle = when {
                PowerSave.intensive -> stringResource(R.string.mono_sub_intensive)
                vm.monoEnabled -> stringResource(R.string.mono_sub_on)
                else -> stringResource(R.string.mono_sub_off)
            },
            checked = vm.monoEnabled || PowerSave.intensive,
            onChange = { vm.changeMono(it) },
            enabled = !PowerSave.intensive,
        )
        SwitchRow(
            title = stringResource(R.string.normalize),
            subtitle = stringResource(if (PowerSave.intensive) R.string.locked_by_intensive else R.string.normalize_sub),
            checked = vm.normalizeEnabled && !PowerSave.intensive,
            onChange = { vm.changeNormalize(it) },
            enabled = !PowerSave.intensive,
        )
        BassControl(vm, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        TrebleControl(vm, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        SubsonicControl(vm, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        SpeedControl(vm, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        ListItem(
            modifier = Modifier.clickable(enabled = !PowerSave.intensive) { vm.push(Screen.Equalizer) },
            leadingContent = { Icon(Icons.Default.Equalizer, null) },
            headlineContent = { Text(stringResource(R.string.title_equalizer)) },
            supportingContent = {
                Text(
                    if (PowerSave.intensive) stringResource(R.string.locked_by_intensive)
                    else stringResource(R.string.eq_row_sub, eqModeLabel(vm.eqMode)),
                )
            },
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()

        // ----- Язык приложения -----
        val appCtx = LocalContext.current
        val currentLanguage = remember { AppLocale.saved(appCtx) }
        var showLanguage by remember { mutableStateOf(false) }
        ListItem(
            modifier = Modifier.clickable { showLanguage = true },
            leadingContent = { Icon(Icons.Default.Language, null) },
            headlineContent = { Text(stringResource(R.string.language_title)) },
            supportingContent = { Text(languageSummary(currentLanguage)) },
        )
        if (showLanguage) {
            LanguageDialog(
                current = currentLanguage,
                onSelect = { lang ->
                    showLanguage = false
                    if (lang != currentLanguage) {
                        // Новый язык применяется при пересоздании активности (attachBaseContext); музыка и очередь живут во ViewModel и не прерываются
                        AppLocale.save(appCtx, lang)
                        appCtx.findActivity()?.recreate()
                    }
                },
                onDismiss = { showLanguage = false },
            )
        }
        HorizontalDivider()

        // ----- Кэш и сброс -----
        var showReset by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { vm.refreshCacheSize() }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TwoStepButton(stringResource(R.string.clear_cache), stringResource(R.string.clear_cache_confirm)) {
                vm.clearAppCache()
                clearCoverMemoryCache()
            }
            Text(
                stringResource(R.string.cache_now, formatBytes(vm.cacheBytes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showReset = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.factory_reset)) }
        }
        if (showReset) {
            FactoryResetDialog(
                onConfirm = { clearCache ->
                    vm.factoryReset(clearCache)
                    if (clearCache) clearCoverMemoryCache()
                    showReset = false
                },
                onDismiss = { showReset = false },
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Кнопка с двумя нажатиями: первое «взводит» её (4 секунды), второе выполняет действие. */
@Composable
private fun TwoStepButton(label: String, armedLabel: String, onConfirm: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(4000)
            armed = false
        }
    }
    if (armed) {
        Button(
            onClick = { armed = false; onConfirm() },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(armedLabel) }
    } else {
        OutlinedButton(onClick = { armed = true }, modifier = Modifier.fillMaxWidth()) { Text(label) }
    }
}

@Composable
private fun FactoryResetDialog(onConfirm: (clearCache: Boolean) -> Unit, onDismiss: () -> Unit) {
    var clearCache by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.factory_reset_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.factory_reset_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().clickable { clearCache = false },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = !clearCache, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.reset_only), Modifier.padding(vertical = 10.dp))
                }
                Row(
                    Modifier.fillMaxWidth().clickable { clearCache = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = clearCache, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.reset_and_clear_cache), Modifier.padding(vertical = 10.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(clearCache) }) {
                Text(stringResource(R.string.reset_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Сбрасывает кэш обложек в памяти (вместе с очисткой дискового кэша). */
internal fun clearCoverMemoryCache() = coverCache.evictAll()

@Composable
private fun formatBytes(b: Long): String {
    val ui = uiLocale()
    return when {
        b < 1024 -> stringResource(R.string.fmt_bytes, b)
        b < 1024 * 1024 -> stringResource(R.string.fmt_kb, String.format(ui, "%.0f", b / 1024.0))
        else -> stringResource(R.string.fmt_mb, String.format(ui, "%.1f", b / 1024.0 / 1024.0))
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled) { onChange(!checked) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { AppSwitch(checked = checked, onCheckedChange = onChange, enabled = enabled) },
    )
}

// =====================================================================
// Эквалайзер
// =====================================================================

/**
 * Экран эквалайзера. Открывается из настроек (fromPlayer = false) и из плеера (fromPlayer = true).
 *
 *  - Из настроек: можно выбирать режим. Ползунки активны только в режиме «для всех треков».
 *    В режиме «для каждого трека» они заблокированы (показывается сохранённая общая настройка).
 *  - Из плеера: режим переключать нельзя. Ползунки активны и в режиме «для всех треков» (меняется общая кривая),
 *    и в режиме «для каждого трека» (меняется кривая играющего трека).
 */
@Composable
private fun EqualizerScreen(vm: PlayerViewModel, fromPlayer: Boolean) {
    val gains = if (fromPlayer) vm.playerGains else vm.globalGains
    val editable = if (fromPlayer) vm.canEditInPlayer else vm.canEditInSettings

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (!fromPlayer) {
            Text(
                stringResource(R.string.eq_mode_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
            )
            val eqModes = listOf(EqMode.OFF, EqMode.GLOBAL, EqMode.PER_TRACK)
            SlidingSelector(
                labels = listOf(stringResource(R.string.eqsel_off), stringResource(R.string.eqsel_global), stringResource(R.string.eqsel_per_track)),
                icons = listOf(Icons.Default.PowerSettingsNew, Icons.Default.Equalizer, Icons.Default.MusicNote),
                selected = eqModes.indexOf(vm.eqMode),
                onSelect = { vm.changeEqMode(eqModes[it]) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SelectorCaption(
                when (vm.eqMode) {
                    EqMode.OFF -> stringResource(R.string.eqdesc_off)
                    EqMode.GLOBAL -> stringResource(R.string.eqdesc_global)
                    EqMode.PER_TRACK -> stringResource(R.string.eqdesc_per_track)
                },
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
        } else {
            Spacer(Modifier.height(8.dp))
        }

        // Пояснение к текущему режиму: что именно сейчас меняют ползунки
        val note = when (vm.eqMode) {
            EqMode.OFF ->
                if (fromPlayer) {
                    stringResource(R.string.eqnote_off_player)
                } else {
                    stringResource(R.string.eqnote_off_settings)
                }
            EqMode.GLOBAL ->
                if (fromPlayer) {
                    if (vm.currentExcludesGlobal) {
                        stringResource(R.string.eqnote_excluded)
                    } else {
                        stringResource(R.string.eqnote_global_player)
                    }
                } else stringResource(R.string.eqnote_global_settings)
            EqMode.PER_TRACK ->
                if (fromPlayer) {
                    vm.currentTrack?.let { stringResource(R.string.eqnote_per_track_player_track, it.title) }
                        ?: stringResource(R.string.eqnote_per_track_player_none)
                } else {
                    stringResource(R.string.eqnote_per_track_settings)
                }
        }
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Text(
                note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(12.dp),
            )
        }

        // Исключение текущего трека из общей настройки
        if (fromPlayer && vm.eqMode != EqMode.OFF) {
            val excluded = vm.currentExcludesGlobal
            Spacer(Modifier.height(4.dp))
            ListItem(
                modifier = Modifier.clickable(enabled = vm.currentId != null) { vm.changeGlobalExcluded(!excluded) },
                headlineContent = { Text(stringResource(R.string.eq_exclude_title)) },
                supportingContent = {
                    Text(
                        when {
                            vm.eqMode == EqMode.PER_TRACK && excluded ->
                                stringResource(R.string.eq_exclude_sub_pt_excluded)
                            vm.eqMode == EqMode.PER_TRACK ->
                                stringResource(R.string.eq_exclude_sub_pt)
                            excluded -> stringResource(R.string.eq_exclude_sub_on)
                            else -> stringResource(R.string.eq_exclude_sub_off)
                        },
                    )
                },
                trailingContent = {
                    AppSwitch(
                        checked = excluded,
                        onCheckedChange = { vm.changeGlobalExcluded(it) },
                        enabled = vm.currentId != null,
                    )
                },
            )
        } else {
            Spacer(Modifier.height(12.dp))
        }

        // Пресеты: работают так же, как ручная настройка (в режиме «для каждого трека» — только в плеере)
        Text(
            stringResource(R.string.presets),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EQ_PRESETS.forEach { p ->
                val selected = (0 until AudioFx.BANDS).all { abs(gains[it] - p.gains[it]) < 0.05f }
                FilterChip(
                    selected = selected,
                    onClick = { vm.applyEqPreset(p, fromPlayer) },
                    enabled = editable,
                    label = { Text(stringResource(p.name)) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        // 12 вертикальных ползунков
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            for (i in 0 until AudioFx.BANDS) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        formatDb(gains[i]),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (editable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BandSlider(
                        value = gains[i],
                        enabled = editable,
                        onChange = { vm.setEqBand(i, it, fromPlayer) },
                        onFinish = { vm.commitSound() },
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                    )
                    Text(bandLabel(i), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Text(
            stringResource(R.string.eq_axis_caption),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { vm.resetEq(fromPlayer) },
            enabled = editable,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Text(stringResource(if (vm.eqMode == EqMode.PER_TRACK) R.string.eq_reset_track else R.string.eq_reset))
        }
        if (vm.eqMode == EqMode.PER_TRACK) {
            Text(
                stringResource(R.string.eq_custom_count, vm.customTrackCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        Text(
            stringResource(R.string.eq_modes_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

/** Эквалайзер, открытый из плеера: своя шапка со стрелкой «Назад» (возвращает в плеер) и без выбора режима. */
@Composable
private fun PlayerEqualizerScreen(vm: PlayerViewModel) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.pop() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Text(
                    stringResource(R.string.title_equalizer),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                EqualizerScreen(vm, fromPlayer = true)
            }
        }
    }
}

/**
 * Вертикальный ползунок одной полосы эквалайзера (−12...+12 дБ, шаг 0,5 дБ).
 * Рисуется напрямую в Canvas, чтобы 12 ползунков работали быстро.
 */
@Composable
private fun BandSlider(
    value: Float,
    enabled: Boolean,
    onChange: (Float) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val changeNow by rememberUpdatedState(onChange)
    val finishNow by rememberUpdatedState(onFinish)
    val range = AudioFx.EQ_RANGE_DB
    val activeColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val zeroColor = MaterialTheme.colorScheme.outline

    fun yToValue(y: Float, height: Float, pad: Float): Float {
        val h = (height - 2 * pad).coerceAtLeast(1f)
        val frac = ((y - pad) / h).coerceIn(0f, 1f)
        val v = range - frac * 2 * range
        return Math.round(v * 2) / 2f
    }

    Box(
        modifier
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val pad = 12.dp.toPx()
                detectTapGestures { offset ->
                    changeNow(yToValue(offset.y, size.height.toFloat(), pad))
                    finishNow()
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val pad = 12.dp.toPx()
                detectVerticalDragGestures(
                    onDragStart = { offset -> changeNow(yToValue(offset.y, size.height.toFloat(), pad)) },
                    onDragEnd = { finishNow() },
                    onDragCancel = { finishNow() },
                ) { change, _ ->
                    change.consume()
                    changeNow(yToValue(change.position.y, size.height.toFloat(), pad))
                }
            }
            .drawBehind {
                val pad = 12.dp.toPx()
                val h = size.height - 2 * pad
                val cx = size.width / 2
                val trackW = 4.dp.toPx()
                val corner = CornerRadius(trackW / 2)
                val y0 = pad + range / (2 * range) * h
                val y1 = pad + (range - value) / (2 * range) * h

                drawRoundRect(trackColor, Offset(cx - trackW / 2, pad), Size(trackW, h), corner)
                drawLine(zeroColor, Offset(cx - 8.dp.toPx(), y0), Offset(cx + 8.dp.toPx(), y0), strokeWidth = 1.dp.toPx())
                drawRoundRect(
                    activeColor, Offset(cx - trackW / 2, minOf(y0, y1)),
                    Size(trackW, abs(y1 - y0)), corner,
                )
                drawCircle(activeColor, 9.dp.toPx(), Offset(cx, y1))
            },
    )
}

/** Подпись полосы эквалайзера: «250», «1k», «3,5k» (десятичный знак и «к»/«k» зависят от языка). */
@Composable
private fun bandLabel(i: Int): String {
    val raw = AudioFx.BAND_LABELS[i]
    if (i < AudioFx.BAND_KHZ_FROM) return raw
    val sep = java.text.DecimalFormatSymbols.getInstance(uiLocale()).decimalSeparator
    return raw.replace('.', sep) + stringResource(R.string.eq_khz_suffix)
}

private fun formatDb(v: Float): String = when {
    v == 0f -> "0"
    v % 1f == 0f -> "%+d".format(v.toInt())
    else -> "%+.1f".format(v)
}

// =====================================================================
// Утилиты
// =====================================================================

@Composable
private fun folderLabel(uri: Uri): String {
    val id = android.provider.DocumentsContract.getTreeDocumentId(uri) // "primary:Music/Rock"
    val (volume, path) = id.split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    val root = if (volume == "primary") stringResource(R.string.storage_internal) else stringResource(R.string.storage_sd, volume)
    return if (path.isEmpty()) root else "$root / ${path.replace("/", " / ")}"
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
