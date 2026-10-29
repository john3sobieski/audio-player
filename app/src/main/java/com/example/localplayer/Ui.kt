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
import androidx.compose.animation.fadeIn
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PowerSettingsNew
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
    if (to.depth > from.depth) {
        // Вперёд: новый экран выезжает справа поверх старого
        ContentTransform(
            slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(250)),
            slideOutHorizontally(tween(300)) { -it / 4 },
        )
    } else {
        // Назад: уходящий экран уезжает вправо, открывая предыдущий под ним
        ContentTransform(
            slideInHorizontally(tween(300)) { -it / 4 } + fadeIn(tween(250)),
            slideOutHorizontally(tween(300)) { it },
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
    val base = vm.stack.lastOrNull { it !is Screen.Player && it !is Screen.PlayerEqualizer }
    val depth = vm.stack.count { it !is Screen.Player && it !is Screen.PlayerEqualizer }

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
                        enter = slideInVertically(tween(250)) { it },
                        exit = slideOutVertically(tween(200)) { it },
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
                            Screen.Player, Screen.PlayerEqualizer -> Unit // рисуются отдельными слоями ниже
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
            enter = slideInVertically(tween(350)) { it },
            exit = slideOutVertically(tween(300)) { it },
        ) {
            PlayerScreen(vm)
        }

        // Эквалайзер из плеера выезжает справа поверх плеера. Кнопка «Назад» возвращает в плеер.
        AnimatedVisibility(
            visible = playerEqOpen,
            enter = slideInHorizontally(tween(300)) { it },
            exit = slideOutHorizontally(tween(250)) { it },
        ) {
            PlayerEqualizerScreen(vm)
        }
    }

    // ----- Диалоги -----
    vm.trackForPlaylist?.let { t ->
        AddToPlaylistDialog(vm, t)
    }
    if (vm.newPlaylistDialog) {
        NameDialog(
            title = "Новый плейлист",
            initial = "",
            onConfirm = { vm.createPlaylist(it, vm.newPlaylistFirstTrack); vm.newPlaylistDialog = false; vm.newPlaylistFirstTrack = null },
            onDismiss = { vm.newPlaylistDialog = false; vm.newPlaylistFirstTrack = null },
        )
    }
    vm.renameTarget?.let { p ->
        NameDialog(
            title = "Переименовать плейлист",
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
        null -> "Моя музыка"
        Screen.Settings -> "Настройки"
        Screen.Equalizer -> "Эквалайзер"
        Screen.Player, Screen.PlayerEqualizer -> "" // у экранов плеера собственная шапка, сюда они не попадают
        is Screen.AlbumScreen -> vm.albums.find { it.key == screen.key }?.name ?: ""
        is Screen.PlaylistScreen -> vm.playlists.find { it.id == screen.id }?.name ?: ""
    }
    val searching = vm.searchOpen && screen == null
    // 0 -> 1: заголовок уезжает влево и гаснет, на его месте раскрывается поле поиска
    val shift by animateFloatAsState(if (searching) 1f else 0f, tween(320), label = "titleShift")
    val barColors = MaterialTheme.colorScheme
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = barColors.surface,
            scrolledContainerColor = barColors.surface,
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
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                    label = "title",
                ) { text ->
                    Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                AnimatedVisibility(
                    visible = searching,
                    enter = fadeIn(tween(250, delayMillis = 120)) + expandHorizontally(tween(320), expandFrom = Alignment.End),
                    exit = fadeOut(tween(150)) + shrinkHorizontally(tween(250), shrinkTowards = Alignment.End),
                ) {
                    SearchField(vm)
                }
            }
        },
        navigationIcon = {
            AnimatedVisibility(visible = screen != null) {
                IconButton(onClick = { vm.pop() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
            }
        },
        actions = {
            // Лупа стоит слева от шестерёнки; при открытом поиске превращается в крестик
            AnimatedVisibility(visible = screen == null && vm.folder != null) {
                IconButton(onClick = { if (searching) vm.closeSearch() else vm.openSearch() }) {
                    Crossfade(targetState = searching, label = "searchIcon") { isOpen ->
                        Icon(
                            if (isOpen) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (isOpen) "Закрыть поиск" else "Поиск",
                        )
                    }
                }
            }
            AnimatedVisibility(visible = screen == null) {
                IconButton(onClick = { vm.push(Screen.Settings) }) {
                    Icon(Icons.Default.Settings, contentDescription = "Настройки")
                }
            }
        },
    )
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
                    0 -> "Поиск плейлистов"
                    1 -> "Поиск альбомов"
                    else -> "Поиск треков"
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
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = cs.onSecondaryContainer,
        selectedTextColor = cs.onSurface,
        indicatorColor = cs.secondaryContainer,
        unselectedIconColor = cs.onSurfaceVariant,
        unselectedTextColor = cs.onSurfaceVariant,
    )
    NavigationBar(containerColor = cs.surfaceContainer) {
        NavigationBarItem(
            selected = vm.tab == 0, onClick = { vm.openTab(0) },
            icon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null) }, label = { Text("Плейлисты") }, colors = itemColors,
        )
        NavigationBarItem(
            selected = vm.tab == 1, onClick = { vm.openTab(1) },
            icon = { Icon(Icons.Default.Album, null) }, label = { Text("Альбомы") }, colors = itemColors,
        )
        NavigationBarItem(
            selected = vm.tab == 2, onClick = { vm.openTab(2) },
            icon = { Icon(Icons.Default.LibraryMusic, null) }, label = { Text("Треки") }, colors = itemColors,
        )
    }
}

// =====================================================================
// Библиотека: 3 вкладки
// =====================================================================

@Composable
private fun LibraryScreen(vm: PlayerViewModel, pickFolder: () -> Unit) {
    if (vm.folder == null) {
        EmptyFolderState(pickFolder)
        return
    }
    Column(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = vm.scanning) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        // Вкладки — страницы горизонтального пейджера: переход прерывается и перенаправляется на лету,
        // поэтому при быстром переключении страницы не накладываются друг на друга. Работает и свайп.
        val pagerState = rememberPagerState(initialPage = vm.tab) { 3 }
        // pending — страница, к которой мы сами прокручиваем по нажатию на нижнюю панель. Пока она не достигнута,
        // промежуточные остановки пейджера не считаются выбором пользователя (раньше они возвращали вкладку назад
        // и из-за этого переход «залипал»).
        var pending by remember { mutableIntStateOf(-1) }
        val dragging by pagerState.interactionSource.collectIsDraggedAsState()
        LaunchedEffect(dragging) { if (dragging) pending = -1 }
        LaunchedEffect(vm.tab) {
            if (pagerState.currentPage != vm.tab || pagerState.targetPage != vm.tab) {
                pending = vm.tab
                pagerState.animateScrollToPage(vm.tab, animationSpec = tween(300, easing = FastOutSlowInEasing))
            }
        }
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }.collect { page ->
                if (page == pending) pending = -1 else if (pending == -1 && page != vm.tab) vm.openTab(page)
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            // Соседние страницы собираются заранее: переход не ждёт отрисовки тяжёлого списка треков
            beyondViewportPageCount = 2,
            key = { it },
        ) { page ->
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when (page) {
                    0 -> PlaylistsTab(vm)
                    1 -> AlbumsTab(vm)
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
        Text("Выберите папку с музыкой", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Плеер будет видеть только файлы из этой папки (и вложенных).",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = pickFolder) { Text("Выбрать папку") }
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
        EmptyHint(if (vm.scanning) "Сканирую папку…" else "В выбранной папке нет аудиофайлов")
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
        if (list.isEmpty()) EmptyHint("Ничего не найдено") else
        FastScrollContainer(listState) {
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            itemsIndexed(list, key = { _, t -> t.uri }) { i, t ->
                TrackRow(
                    track = t,
                    current = vm.currentId == t.uri,
                    number = i + 1,
                    onClick = { vm.play(list, i) },
                    menu = listOf("Добавить в плейлист" to { vm.trackForPlaylist = t }),
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
        EmptyHint(if (vm.scanning) "Сканирую папку…" else "Альбомов пока нет")
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
        if (list.isEmpty()) EmptyHint("Ничего не найдено") else
        FastScrollContainer(listState) {
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            itemsIndexed(list, key = { _, a -> a.key }) { _, a ->
                ListItem(
                    modifier = reorderableItem(reorder, a.key).pressableRow({ vm.push(Screen.AlbumScreen(a.key)) }),
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { Cover(a.tracks.firstOrNull()?.uri, 56.dp) },
                    headlineContent = { Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text("${a.artist} · ${a.tracks.size} ${plural(a.tracks.size)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    val sorted = remember(all, query) { if (query.isEmpty()) all else all.filter { it.matches(query) } }
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
                    headlineContent = { Text("Новый плейлист", color = MaterialTheme.colorScheme.primary) },
                )
                HorizontalDivider()
            }
            if (vm.playlists.isEmpty()) {
                item { Text("Плейлистов пока нет", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else if (list.isEmpty()) {
                item { Text("Ничего не найдено", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                    headlineContent = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("$n ${plural(n)}") },
                    trailingContent = {
                        // Плейлист по умолчанию нельзя переименовать или удалить
                        if (p.id != FAVORITES_ID) {
                            RowMenu(
                                listOf(
                                    "Переименовать" to { vm.renameTarget = p },
                                    "Удалить" to { vm.deletePlaylist(p.id) },
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
// Экраны альбома и плейлиста
// =====================================================================

@Composable
private fun AlbumScreen(vm: PlayerViewModel, key: String) {
    val album = vm.albums.find { it.key == key }
    if (album == null) { EmptyHint("Альбом не найден"); return }
    TrackListScreen(
        vm = vm,
        header = "${album.artist} · ${album.tracks.size} ${plural(album.tracks.size)}",
        list = album.tracks,
        showCovers = false, // внутри альбома обложка у всех треков одна: показываем её один раз сверху
        coverUri = album.tracks.firstOrNull()?.uri,
        menuFor = { t -> listOf("Добавить в плейлист" to { vm.trackForPlaylist = t }) },
    )
}

@Composable
private fun PlaylistScreen(vm: PlayerViewModel, id: String) {
    val pl = vm.playlists.find { it.id == id }
    if (pl == null) { EmptyHint("Плейлист не найден"); return }
    val list = vm.playlistTracks(pl).distinctBy { it.uri }
    TrackListScreen(
        vm = vm,
        header = "${list.size} ${plural(list.size)}",
        list = list,
        emptyText = "Плейлист пуст. Добавляйте треки через меню «⋮» на вкладках «Треки» и «Альбомы».",
        menuFor = { t -> listOf("Убрать из плейлиста" to { vm.removeFromPlaylist(pl.id, t) }) },
        onReorder = { uris -> vm.reorderPlaylistTracks(pl.id, uris) },
    )
}

@Composable
private fun TrackListScreen(
    vm: PlayerViewModel,
    header: String,
    list: List<Track>,
    emptyText: String = "Нет треков",
    showCovers: Boolean = true,
    /** Если задан, над заголовком показывается большая обложка (экран альбома). */
    coverUri: String? = null,
    menuFor: (Track) -> List<Pair<String, () -> Unit>>,
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
                    ActionPill("Играть", Icons.Default.PlayArrow, primary = true, enabled = list.isNotEmpty(), onClick = { vm.play(list, 0) })
                    ActionPill(
                        "Вперемешку", Icons.Default.Shuffle, primary = false, enabled = list.isNotEmpty(),
                        onClick = { vm.playShuffled(list) }, spin = true,
                    )
                }
            }
        }
        if (list.isEmpty()) item { Text(emptyText, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        itemsIndexed(shown, key = { _, t -> t.uri }) { i, t ->
            TrackRow(
                t, vm.currentId == t.uri, i + 1, { vm.play(shown, i) }, menuFor(t),
                if (onReorder != null) reorderableItem(reorder, t.uri) else Modifier.animateItem(
                    fadeInSpec = null,
                    fadeOutSpec = null,
                    placementSpec = if (FastScroll.active) null else androidx.compose.animation.core.spring(
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
    menu: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier,
    showCover: Boolean = true,
) {
    val color by animateColorAsState(
        if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        tween(300), label = "trackColor",
    )
    ListItem(
        modifier = modifier.pressableRow(onClick, highlighted = current),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Порядковый номер (до 4–5 цифр влезает без переноса); у играющего трека вместо него треугольник
                Box(Modifier.width(TRACK_NUMBER_WIDTH), contentAlignment = Alignment.Center) {
                    if (current) {
                        Icon(Icons.Default.PlayArrow, "Сейчас играет", Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
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
        supportingContent = { Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.durationMs > 0) Text(formatTime(track.durationMs), style = MaterialTheme.typography.bodySmall)
                RowMenu(menu)
            }
        },
    )
}

@Composable
private fun RowMenu(items: List<Pair<String, () -> Unit>>) = AppMenu(items)

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
        Crossfade(targetState = bmp, animationSpec = tween(if (fastScrolling) 0 else 200), label = "cover") { b ->
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
                    "Добавить в плейлист",
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
                        Text("Новый плейлист")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = close) { Text("Готово") }
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
            Text(p.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "$n ${plural(n)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(Modifier.size(20.dp)) {
            if (contains) Icon(Icons.Default.Check, "В плейлисте", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Название") }) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

// =====================================================================
// Мини-плеер и полный плеер
// =====================================================================

@Composable
private fun MiniPlayer(vm: PlayerViewModel) {
    val t = vm.currentTrack
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clickable { vm.openPlayer() },
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(t?.uri, 44.dp, decodeSize = PLAYER_COVER_SIZE)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(t?.title ?: "…", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text(t?.artist ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            PlayPauseButton(vm.uiPlaying, { vm.togglePlay() }, buttonSize = 40.dp, iconSize = 20.dp)
            Spacer(Modifier.width(4.dp))
            SkipButton(Icons.Default.SkipNext, "Следующий", 1, { vm.next() }, boxSize = 44.dp, iconSize = 26.dp)
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
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Свернуть")
                }
                Text(
                    "Сейчас играет",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
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
                        TrackInfoButton(enabled = t != null) { showInfo = true }
                        AnimatedContent(
                            targetState = t,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                            label = "nowPlayingTitle",
                        ) { tr ->
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    tr?.title ?: "", style = MaterialTheme.typography.titleLarge,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                                )
                                Text(
                                    tr?.artist ?: "", style = MaterialTheme.typography.bodyLarge,
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

/** Круглая кнопка «i» слева от названия: открывает окно со всеми данными о треке. */
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
        description = if (saved) "Изменить плейлисты трека" else "Добавить в плейлист",
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
        title = { Text("О треке") },
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
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
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
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
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
 * «EQ» открывает эквалайзер. Включённые эффекты подсвечены.
 */
@Composable
private fun SoundPanel(vm: PlayerViewModel) {
    var dialog by remember { mutableStateOf<Int?>(null) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        SoundButton("Bass", vm.bassEnabled, Modifier.weight(1f)) { dialog = 0 }
        SoundButton("Treble", vm.trebleEnabled, Modifier.weight(1f)) { dialog = 1 }
        SoundButton("Subsonic", vm.subEnabled, Modifier.weight(1f)) { dialog = 2 }
        SoundButton("EQ", vm.eqMode != EqMode.OFF, Modifier.weight(1f)) { vm.openPlayerEqualizer() }
        // Включённая скорость показывается прямо на кнопке («1,5x»)
        SoundButton(
            if (vm.speedEnabled) speedLabel(vm.speedValue) else "Speed",
            vm.speedEnabled, Modifier.weight(1f),
        ) { dialog = 3 }
    }
    dialog?.let { d ->
        AlertDialog(
            onDismissRequest = { dialog = null },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Готово") } },
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

@Composable
private fun SoundButton(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val content: @Composable RowScope.() -> Unit = {
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
    }
    val pad = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
    if (active) {
        FilledTonalButton(onClick = onClick, modifier = modifier, contentPadding = pad, content = content)
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, contentPadding = pad, content = content)
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
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    if (enabled) valueText else "Выключено",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
        Slider(
            value = value,
            onValueChange = onValue,
            onValueChangeFinished = onFinish,
            valueRange = range,
            steps = steps,
            enabled = enabled,
        )
    }
}

/** Усиление басов: переключатель и ползунок 0...+15 дБ. Работает независимо от эквалайзера. */
@Composable
private fun BassControl(vm: PlayerViewModel, modifier: Modifier = Modifier) = FxControl(
    title = "Усиление басов",
    enabled = vm.bassEnabled,
    valueText = "+${vm.bassDb.roundToInt()} дБ",
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
    title = "Усиление требла",
    enabled = vm.trebleEnabled,
    valueText = "+${vm.trebleDb.roundToInt()} дБ",
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
            title = "Скорость воспроизведения",
            enabled = vm.speedEnabled,
            valueText = speedLabel(vm.speedValue) +
                when {
                    vm.speedValue > 0f -> " · быстрее"
                    vm.speedValue < 0f -> " · медленнее"
                    else -> " · обычная"
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
                enabled = vm.speedEnabled && vm.speedValue > AudioFx.SPEED_MIN + 0.05f,
            ) { Text("Медленнее") }
            TextButton(
                onClick = { vm.changeSpeedValue(0f); vm.commitSound() },
                enabled = vm.speedEnabled && vm.speedValue != 0f,
            ) { Text("1x") }
            TextButton(
                onClick = { vm.changeSpeedValue(vm.speedValue + 0.1f); vm.commitSound() },
                enabled = vm.speedEnabled && vm.speedValue < AudioFx.SPEED_MAX - 0.05f,
            ) { Text("Быстрее") }
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
    title = "Subsonic-фильтр",
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

private fun eqModeLabel(mode: EqMode): String = when (mode) {
    EqMode.OFF -> "выключен"
    EqMode.GLOBAL -> "для всех треков"
    EqMode.PER_TRACK -> "для каждого трека"
}

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
                withFrameNanos { }
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
            Icon(Icons.Default.Shuffle, "Перемешать", tint = tint)
        }
        SkipButton(Icons.Default.SkipPrevious, "Предыдущий", -1, { vm.prev() })
        PlayPauseButton(vm.uiPlaying, { vm.togglePlay() }, buttonSize = 72.dp, iconSize = 34.dp)
        SkipButton(Icons.Default.SkipNext, "Следующий", 1, { vm.next() })
        ToggleIconButton(active = vm.repeat != Player.REPEAT_MODE_OFF, stateKey = vm.repeat, onClick = { vm.cycleRepeat() }) { tint ->
            AnimatedContent(
                targetState = vm.repeat == Player.REPEAT_MODE_ONE,
                transitionSpec = {
                    (scaleIn(spring(dampingRatio = 0.6f, stiffness = 500f)) + fadeIn(tween(120))) togetherWith
                        (scaleOut(tween(100)) + fadeOut(tween(100)))
                },
                label = "repeatIcon",
            ) { one ->
                Icon(if (one) Icons.Default.RepeatOne else Icons.Default.Repeat, "Повтор", tint = tint)
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
            Text("Папка с музыкой", style = MaterialTheme.typography.titleMedium)
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        vm.folder?.let { folderLabel(it) } ?: "Не выбрана",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (vm.folder != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (vm.scanning) "Сканирую…" else "Найдено треков: ${vm.tracks.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Button(onClick = pickFolder, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Выбрать папку")
            }
            OutlinedButton(
                onClick = { vm.rescan(force = true) },
                enabled = vm.folder != null && !vm.scanning,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Пересканировать папку") }
            Text(
                "Плеер читает только выбранную папку и её подпапки и обновляет список при каждом входе в приложение.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider()

        // ----- Тема -----
        Text(
            "Тема",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        val themeModes = ThemeMode.entries
        SlidingSelector(
            labels = themeModes.map { it.label },
            icons = listOf(Icons.Default.LightMode, Icons.Default.DarkMode, Icons.Default.SettingsBrightness),
            selected = themeModes.indexOf(vm.themeMode),
            onSelect = { vm.changeTheme(themeModes[it]) },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        SelectorCaption(vm.themeMode.description)
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()

        // ----- Звук -----
        Text(
            "Звук",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        SwitchRow(
            title = "Моно",
            subtitle = if (vm.monoEnabled) "Левый и правый каналы объединены" else "Стерео: левый и правый каналы раздельно",
            checked = vm.monoEnabled,
            onChange = { vm.changeMono(it) },
        )
        SwitchRow(
            title = "Нормализация громкости",
            subtitle = "Выравнивает громкость разных треков, чтобы не крутить ползунок вручную",
            checked = vm.normalizeEnabled,
            onChange = { vm.changeNormalize(it) },
        )
        BassControl(vm, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        TrebleControl(vm, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        SubsonicControl(vm, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        SpeedControl(vm, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        ListItem(
            modifier = Modifier.clickable { vm.push(Screen.Equalizer) },
            leadingContent = { Icon(Icons.Default.Equalizer, null) },
            headlineContent = { Text("Эквалайзер") },
            supportingContent = { Text("12 полос · режим: ${eqModeLabel(vm.eqMode)}") },
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()

        // ----- Кэш и сброс -----
        var showReset by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { vm.refreshCacheSize() }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TwoStepButton("Очистить кэш приложения", "Нажмите ещё раз для подтверждения") {
                vm.clearAppCache()
                clearCoverMemoryCache()
            }
            Text(
                "В кэше сейчас: ${formatBytes(vm.cacheBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showReset = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Сбросить до заводских настроек") }
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
        title = { Text("Сбросить до заводских настроек?") },
        text = {
            Column {
                Text(
                    "Будут сброшены все настройки звука (моно, нормализация, басы, требл, subsonic, скорость), " +
                        "все эквалайзеры (общий и отдельных треков) и сортировки. " +
                        "Плейлисты и выбранная папка сохранятся.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().clickable { clearCache = false },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = !clearCache, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Text("Только сбросить настройки", Modifier.padding(vertical = 10.dp))
                }
                Row(
                    Modifier.fillMaxWidth().clickable { clearCache = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = clearCache, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Text("Сбросить настройки и очистить весь кэш", Modifier.padding(vertical = 10.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(clearCache) }) {
                Text("Сбросить", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Сбрасывает кэш обложек в памяти (вместе с очисткой дискового кэша). */
internal fun clearCoverMemoryCache() = coverCache.evictAll()

private fun formatBytes(b: Long): String = when {
    b < 1024 -> "$b Б"
    b < 1024 * 1024 -> String.format(java.util.Locale("ru"), "%.0f КБ", b / 1024.0)
    else -> String.format(java.util.Locale("ru"), "%.1f МБ", b / 1024.0 / 1024.0)
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onChange(!checked) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
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
                "Режим",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
            )
            val eqModes = listOf(EqMode.OFF, EqMode.GLOBAL, EqMode.PER_TRACK)
            SlidingSelector(
                labels = listOf("Выключен", "Для всех", "Для каждого"),
                icons = listOf(Icons.Default.PowerSettingsNew, Icons.Default.Equalizer, Icons.Default.MusicNote),
                selected = eqModes.indexOf(vm.eqMode),
                onSelect = { vm.changeEqMode(eqModes[it]) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SelectorCaption(
                when (vm.eqMode) {
                    EqMode.OFF -> "Звук без обработки"
                    EqMode.GLOBAL -> "Одна настройка действует на все треки"
                    EqMode.PER_TRACK -> "Своя настройка у каждого трека, запоминается навсегда"
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
                    "Эквалайзер выключен. Включить его и выбрать режим можно в настройках."
                } else {
                    "Эквалайзер выключен. Выберите режим выше, чтобы настроить звук."
                }
            EqMode.GLOBAL ->
                if (fromPlayer) {
                    if (vm.currentExcludesGlobal) {
                        "Для этого трека общая настройка отключена: ползунки меняют только его собственную настройку, она запоминается."
                    } else {
                        "Включён режим «для всех треков»: изменения действуют на все треки. Сменить режим можно в настройках."
                    }
                } else "Режим «для всех треков»: настройка действует на все треки. Её можно менять и в плеере."
            EqMode.PER_TRACK ->
                if (fromPlayer) {
                    vm.currentTrack?.let { "Включён режим «для каждого трека». Настройка относится к треку «${it.title}» и запоминается." }
                        ?: "Включён режим «для каждого трека». Запустите трек, чтобы настроить его звук."
                } else {
                    "Режим «для каждого трека»: здесь настройка заблокирована. Менять звук можно только в плеере, отдельно для каждого трека. " +
                        "Ниже показана общая настройка: она сохранена, но сейчас не действует."
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
                headlineContent = { Text("Не применять общий эквалайзер к этому треку") },
                supportingContent = {
                    Text(
                        when {
                            vm.eqMode == EqMode.PER_TRACK && excluded ->
                                "Сработает в режиме «для всех треков»: трек будет играть только со своей настройкой"
                            vm.eqMode == EqMode.PER_TRACK ->
                                "Сейчас режим «для каждого трека»: можно включить заранее для режима «для всех треков»"
                            excluded -> "Включено: на этот трек не влияет общая настройка"
                            else -> "Выключено: трек играет с общей настройкой"
                        },
                    )
                },
                trailingContent = {
                    Switch(
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
            "Пресеты",
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
                    label = { Text(p.name) },
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
                    Text(AudioFx.BAND_LABELS[i], style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Text(
            "Гц · диапазон ±12 дБ",
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
            Text(if (vm.eqMode == EqMode.PER_TRACK) "Сбросить этот трек" else "Сбросить")
        }
        if (vm.eqMode == EqMode.PER_TRACK) {
            Text(
                "Треков с собственной настройкой: ${vm.customTrackCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        Text(
            "Настройки каждого режима хранятся отдельно и не стираются при переключении.",
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
                Text(
                    "Эквалайзер",
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

private fun formatDb(v: Float): String = when {
    v == 0f -> "0"
    v % 1f == 0f -> "%+d".format(v.toInt())
    else -> "%+.1f".format(v)
}

// =====================================================================
// Утилиты
// =====================================================================

private fun folderLabel(uri: Uri): String {
    val id = android.provider.DocumentsContract.getTreeDocumentId(uri) // "primary:Music/Rock"
    val (volume, path) = id.split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    val root = if (volume == "primary") "Внутренняя память" else "Карта памяти ($volume)"
    return if (path.isEmpty()) root else "$root / ${path.replace("/", " / ")}"
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

private fun plural(n: Int): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m100 in 11..14 -> "треков"
        m10 == 1 -> "трек"
        m10 in 2..4 -> "трека"
        else -> "треков"
    }
}
