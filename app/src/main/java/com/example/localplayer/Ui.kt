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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FolderOpen
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    val playerOpen = vm.screen == Screen.Player
    val base = vm.stack.lastOrNull { it !is Screen.Player }
    val depth = vm.stack.count { it !is Screen.Player }

    BackHandler(enabled = vm.screen != null) { vm.pop() }

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
                            Screen.Equalizer -> EqualizerScreen(vm)
                            Screen.Player -> Unit // плеер рисуется отдельным слоем ниже
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
        Screen.Player -> "" // плеер имеет собственную шапку и сюда не попадает
        is Screen.AlbumScreen -> vm.albums.find { it.key == screen.key }?.name ?: ""
        is Screen.PlaylistScreen -> vm.playlists.find { it.id == screen.id }?.name ?: ""
    }
    TopAppBar(
        title = {
            AnimatedContent(
                targetState = title,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                label = "title",
            ) { text ->
                Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            AnimatedVisibility(visible = screen == null) {
                IconButton(onClick = { vm.push(Screen.Settings) }) {
                    Icon(Icons.Default.Settings, contentDescription = "Настройки")
                }
            }
        },
    )
}

@Composable
private fun LibraryNavBar(vm: PlayerViewModel) {
    NavigationBar {
        NavigationBarItem(
            selected = vm.tab == 0, onClick = { vm.openTab(0) },
            icon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null) }, label = { Text("Плейлисты") },
        )
        NavigationBarItem(
            selected = vm.tab == 1, onClick = { vm.openTab(1) },
            icon = { Icon(Icons.Default.Album, null) }, label = { Text("Альбомы") },
        )
        NavigationBarItem(
            selected = vm.tab == 2, onClick = { vm.openTab(2) },
            icon = { Icon(Icons.Default.LibraryMusic, null) }, label = { Text("Треки") },
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
        AnimatedContent(
            targetState = vm.tab,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                ContentTransform(
                    slideInHorizontally(tween(300)) { it * dir / 3 } + fadeIn(tween(250)),
                    slideOutHorizontally(tween(300)) { -it * dir / 3 },
                )
            },
            label = "tabs",
        ) { tab ->
            // Непрозрачный фон: входящая вкладка полностью закрывает уходящую, поэтому той не нужно затухание
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when (tab) {
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

@Composable
private fun TracksTab(vm: PlayerViewModel) {
    val list = vm.tracks
    if (list.isEmpty()) {
        EmptyHint(if (vm.scanning) "Сканирую папку…" else "В выбранной папке нет аудиофайлов")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(list, key = { _, t -> t.uri }) { i, t ->
            TrackRow(
                track = t,
                current = vm.currentId == t.uri,
                onClick = { vm.play(list, i) },
                menu = listOf("Добавить в плейлист" to { vm.trackForPlaylist = t }),
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun AlbumsTab(vm: PlayerViewModel) {
    val list = vm.albums
    if (list.isEmpty()) {
        EmptyHint(if (vm.scanning) "Сканирую папку…" else "Альбомов пока нет")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(list, key = { _, a -> a.key }) { _, a ->
            ListItem(
                modifier = Modifier.animateItem().clickable { vm.push(Screen.AlbumScreen(a.key)) },
                leadingContent = { Cover(a.tracks.firstOrNull()?.uri, 56.dp) },
                headlineContent = { Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Text("${a.artist} · ${a.tracks.size} ${plural(a.tracks.size)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
            )
        }
    }
}

@Composable
private fun PlaylistsTab(vm: PlayerViewModel) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ListItem(
                modifier = Modifier.clickable { vm.newPlaylistFirstTrack = null; vm.newPlaylistDialog = true },
                leadingContent = { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) },
                headlineContent = { Text("Новый плейлист", color = MaterialTheme.colorScheme.primary) },
            )
            HorizontalDivider()
        }
        if (vm.playlists.isEmpty()) {
            item { Text("Плейлистов пока нет", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        itemsIndexed(vm.playlists, key = { _, p -> p.id }) { _, p ->
            val n = vm.playlistTracks(p).size
            ListItem(
                modifier = Modifier.animateItem().clickable { vm.push(Screen.PlaylistScreen(p.id)) },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null) },
                headlineContent = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text("$n ${plural(n)}") },
                trailingContent = {
                    RowMenu(
                        listOf(
                            "Переименовать" to { vm.renameTarget = p },
                            "Удалить" to { vm.deletePlaylist(p.id) },
                        ),
                    )
                },
            )
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
        menuFor = { t -> listOf("Добавить в плейлист" to { vm.trackForPlaylist = t }) },
    )
}

@Composable
private fun PlaylistScreen(vm: PlayerViewModel, id: String) {
    val pl = vm.playlists.find { it.id == id }
    if (pl == null) { EmptyHint("Плейлист не найден"); return }
    val list = vm.playlistTracks(pl)
    TrackListScreen(
        vm = vm,
        header = "${list.size} ${plural(list.size)}",
        list = list,
        emptyText = "Плейлист пуст. Добавляйте треки через меню «⋮» на вкладках «Треки» и «Альбомы».",
        menuFor = { t -> listOf("Убрать из плейлиста" to { vm.removeFromPlaylist(pl.id, t) }) },
    )
}

@Composable
private fun TrackListScreen(
    vm: PlayerViewModel,
    header: String,
    list: List<Track>,
    emptyText: String = "Нет треков",
    menuFor: (Track) -> List<Pair<String, () -> Unit>>,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(16.dp)) {
                Text(header, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { vm.play(list, 0) }, enabled = list.isNotEmpty()) {
                        Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Играть")
                    }
                    FilledTonalButton(onClick = { vm.playShuffled(list) }, enabled = list.isNotEmpty()) {
                        Icon(Icons.Default.Shuffle, null); Spacer(Modifier.width(6.dp)); Text("Вперемешку")
                    }
                }
            }
        }
        if (list.isEmpty()) item { Text(emptyText, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        itemsIndexed(list, key = { i, t -> "$i|${t.uri}" }) { i, t ->
            TrackRow(t, vm.currentId == t.uri, { vm.play(list, i) }, menuFor(t), Modifier.animateItem())
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
    onClick: () -> Unit,
    menu: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier,
) {
    val color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        leadingContent = {
            Icon(
                if (current) Icons.Default.PlayArrow else Icons.Default.MusicNote, null,
                tint = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
private fun RowMenu(items: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Меню") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { (label, action) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; action() })
            }
        }
    }
}

/** Размер обложки в полноэкранном плеере. Мини-плеер декодирует обложку в том же размере, чтобы при открытии плеера она уже была в кэше. */
private val PLAYER_COVER_SIZE = 280.dp

/** Не больше двух одновременных декодирований обложек, чтобы прокрутка списка не забивала процессор. */
private val coverGate = Semaphore(2)

/** Кэш обложек в памяти: повторное открытие плеера и прокрутка списков не декодируют картинки заново. */
private val coverCache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}

/** Декодирует встроенную обложку сразу в нужном размере (а не в полном разрешении), чтобы не нагружать память и GPU. */
private fun decodeCover(ctx: Context, uri: String, targetPx: Int): ImageBitmap? {
    val r = MediaMetadataRetriever()
    return try {
        r.setDataSource(ctx, Uri.parse(uri))
        val data = r.embeddedPicture ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        val minSide = minOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (minSide / (sample * 2) >= targetPx) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(data, 0, data.size, opts)?.asImageBitmap()
    } catch (_: Exception) {
        null
    } finally {
        runCatching { r.release() }
    }
}

@Composable
private fun Cover(uri: String?, size: Dp, decodeSize: Dp = size) {
    val ctx = LocalContext.current
    val targetPx = with(LocalDensity.current) { decodeSize.roundToPx() }
    val key = "$uri@$targetPx"
    val bmp by produceState<ImageBitmap?>(if (uri == null) null else coverCache.get(key), key) {
        if (uri == null) {
            value = null
            return@produceState
        }
        val cached = coverCache.get(key)
        if (cached != null) {
            value = cached
            return@produceState
        }
        value = withContext(Dispatchers.IO) { coverGate.withPermit { decodeCover(ctx, uri, targetPx) } }
            ?.also { coverCache.put(key, it) }
    }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = bmp, label = "cover") { b ->
            if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AddToPlaylistDialog(vm: PlayerViewModel, track: Track) {
    AlertDialog(
        onDismissRequest = { vm.trackForPlaylist = null },
        title = { Text("Добавить в плейлист") },
        text = {
            Column {
                ListItem(
                    modifier = Modifier.clickable {
                        vm.newPlaylistFirstTrack = track
                        vm.newPlaylistDialog = true
                        vm.trackForPlaylist = null
                    },
                    leadingContent = { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) },
                    headlineContent = { Text("Новый плейлист…", color = MaterialTheme.colorScheme.primary) },
                )
                vm.playlists.forEach { p ->
                    ListItem(
                        modifier = Modifier.clickable { vm.addToPlaylist(p.id, track); vm.trackForPlaylist = null },
                        headlineContent = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { vm.trackForPlaylist = null }) { Text("Отмена") } },
    )
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
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth().clickable { vm.openPlayer() },
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(t?.uri, 44.dp, decodeSize = PLAYER_COVER_SIZE)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(t?.title ?: "…", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text(t?.artist ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { vm.togglePlay() }) {
                Crossfade(targetState = vm.isPlaying, label = "miniPlay") { playing ->
                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Пауза/плей")
                }
            }
            IconButton(onClick = { vm.next() }) { Icon(Icons.Default.SkipNext, "Следующий") }
        }
    }
}

/** Полноэкранный плеер. Рисуется отдельным слоем поверх приложения, поэтому у него своя шапка и свои отступы под системные панели. */
@Composable
private fun PlayerScreen(vm: PlayerViewModel) {
    val t = vm.currentTrack
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
            // Содержимое прокручивается: на небольших экранах панель звука не помещается рядом с обложкой
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))
                Cover(t?.uri, PLAYER_COVER_SIZE)
                Spacer(Modifier.height(24.dp))
                AnimatedContent(
                    targetState = t,
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
                Spacer(Modifier.height(16.dp))
                SeekBar(vm, t?.durationMs ?: 0L)
                Spacer(Modifier.height(8.dp))
                PlayerControls(vm)
                Spacer(Modifier.height(16.dp))
                SoundPanel(vm)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** Быстрые настройки звука прямо в плеере: усиление басов (можно менять во время прослушивания) и переход к эквалайзеру. */
@Composable
private fun SoundPanel(vm: PlayerViewModel) {
    Surface(tonalElevation = 2.dp, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            BassControl(vm)
            TextButton(onClick = { vm.openEqualizerFromPlayer() }) {
                Icon(Icons.Default.Equalizer, null)
                Spacer(Modifier.width(8.dp))
                Text("Эквалайзер: ${eqModeLabel(vm.eqMode)}")
            }
        }
    }
}

/**
 * Усиление басов: переключатель и ползунок 0...+15 дБ. Используется и в плеере, и в настройках.
 * Доступно только при выключенном эквалайзере.
 */
@Composable
private fun BassControl(vm: PlayerViewModel, modifier: Modifier = Modifier) {
    val available = vm.eqMode == EqMode.OFF
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Усиление басов", style = MaterialTheme.typography.titleSmall)
                Text(
                    when {
                        !available -> "Недоступно: сначала выключите эквалайзер"
                        vm.bassEnabled -> "+${vm.bassDb.roundToInt()} дБ"
                        else -> "Выключено"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = vm.bassEnabled,
                onCheckedChange = { vm.changeBassEnabled(it) },
                enabled = available,
            )
        }
        Slider(
            value = vm.bassDb,
            onValueChange = { vm.changeBassDb(it) },
            onValueChangeFinished = { vm.commitSound() },
            valueRange = 0f..AudioFx.BASS_MAX_DB,
            steps = AudioFx.BASS_MAX_DB.toInt() - 1,
            enabled = available && vm.bassEnabled,
        )
    }
}

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
    val shuffleTint by animateColorAsState(
        if (vm.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "shuffleTint",
    )
    val repeatTint by animateColorAsState(
        if (vm.repeat != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "repeatTint",
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { vm.toggleShuffle() }) {
            Icon(Icons.Default.Shuffle, "Перемешать", tint = shuffleTint)
        }
        IconButton(onClick = { vm.prev() }) { Icon(Icons.Default.SkipPrevious, "Предыдущий", Modifier.size(36.dp)) }
        FilledIconButton(onClick = { vm.togglePlay() }, modifier = Modifier.size(72.dp)) {
            Crossfade(targetState = vm.isPlaying, label = "bigPlay") { playing ->
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Пауза/плей", Modifier.size(40.dp))
            }
        }
        IconButton(onClick = { vm.next() }) { Icon(Icons.Default.SkipNext, "Следующий", Modifier.size(36.dp)) }
        IconButton(onClick = { vm.cycleRepeat() }) {
            Icon(
                if (vm.repeat == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                "Повтор",
                tint = repeatTint,
            )
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
            Surface(tonalElevation = 2.dp, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
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
        ListItem(
            modifier = Modifier.clickable { vm.push(Screen.Equalizer) },
            leadingContent = { Icon(Icons.Default.Equalizer, null) },
            headlineContent = { Text("Эквалайзер") },
            supportingContent = { Text("12 полос · режим: ${eqModeLabel(vm.eqMode)}") },
        )
        Spacer(Modifier.height(16.dp))
    }
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

@Composable
private fun EqualizerScreen(vm: PlayerViewModel) {
    val gains = vm.editableGains
    val editable = vm.canEditEq

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            "Режим",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        EqModeRow(vm, EqMode.OFF, "Выключен", "Звук без обработки")
        EqModeRow(vm, EqMode.GLOBAL, "Для всех треков", "Одна настройка действует на все треки")
        EqModeRow(vm, EqMode.PER_TRACK, "Для каждого трека", "Своя настройка у каждого трека, запоминается навсегда")
        if (vm.bassEnabled) {
            Text(
                "Чтобы включить эквалайзер, сначала отключите усиление басов (в настройках или на экране плеера).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Text(
            when (vm.eqMode) {
                EqMode.OFF -> "Эквалайзер выключен. Выберите режим выше, чтобы настроить звук."
                EqMode.GLOBAL -> "Настройка действует на все треки"
                EqMode.PER_TRACK -> vm.currentTrack?.let { "Настройка для трека: ${it.title}" }
                    ?: "Запустите трек, чтобы настроить его звук"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
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
                        onChange = { vm.setEqBand(i, it) },
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
            onClick = { vm.resetEq() },
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

@Composable
private fun EqModeRow(vm: PlayerViewModel, mode: EqMode, title: String, subtitle: String) {
    // Любой режим эквалайзера недоступен, пока включены басы
    val blocked = mode != EqMode.OFF && vm.bassEnabled
    ListItem(
        modifier = Modifier
            .alpha(if (blocked) 0.5f else 1f)
            .clickable(enabled = !blocked) { vm.changeEqMode(mode) },
        leadingContent = { RadioButton(selected = vm.eqMode == mode, onClick = null, enabled = !blocked) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
    )
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
