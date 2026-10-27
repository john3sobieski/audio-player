package com.example.localplayer

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// =====================================================================
// Анимации переходов
// =====================================================================

/** Экран + глубина стека: по глубине понимаем, идём мы «вперёд» или «назад». */
private data class NavState(val screen: Screen?, val depth: Int)

private fun screenTransition(from: NavState, to: NavState): ContentTransform = when {
    // Полноэкранный плеер выезжает снизу и уезжает вниз
    to.screen == Screen.Player && from.screen != Screen.Player ->
        ContentTransform(
            slideInVertically(tween(350)) { it } + fadeIn(tween(250)),
            fadeOut(tween(250)),
            targetContentZIndex = 1f,
        )
    from.screen == Screen.Player && to.screen != Screen.Player ->
        ContentTransform(
            fadeIn(tween(250)),
            slideOutVertically(tween(300)) { it } + fadeOut(tween(250)),
            targetContentZIndex = -1f,
        )
    // Остальные экраны: небольшой сдвиг по горизонтали + затухание
    to.depth > from.depth ->
        ContentTransform(
            slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(300)),
            slideOutHorizontally(tween(300)) { -it / 4 } + fadeOut(tween(200)),
        )
    else ->
        ContentTransform(
            slideInHorizontally(tween(300)) { -it / 4 } + fadeIn(tween(300)),
            slideOutHorizontally(tween(300)) { it / 4 } + fadeOut(tween(200)),
        )
}

// =====================================================================
// Корень приложения
// =====================================================================

@Composable
fun App(vm: PlayerViewModel) {
    val screen = vm.screen
    BackHandler(enabled = screen != null) { vm.pop() }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.selectFolder(uri)
    }

    Scaffold(
        topBar = { TopBar(vm, screen) },
        bottomBar = {
            Column {
                AnimatedVisibility(
                    visible = vm.currentId != null && screen != Screen.Player,
                    enter = slideInVertically(tween(250)) { it } + fadeIn(tween(250)),
                    exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(200)),
                ) {
                    MiniPlayer(vm, bottomInset = screen != null)
                }
                AnimatedVisibility(
                    visible = screen == null,
                    enter = slideInVertically(tween(250)) { it } + fadeIn(tween(250)),
                    exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(200)),
                ) {
                    LibraryNavBar(vm)
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            AnimatedContent(
                targetState = NavState(screen, vm.stack.size),
                transitionSpec = { screenTransition(initialState, targetState) },
                label = "screens",
            ) { state ->
                // Непрозрачный фон, чтобы при переходе экраны не просвечивали друг через друга
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (val s = state.screen) {
                        null -> LibraryScreen(vm) { folderPicker.launch(null) }
                        Screen.Settings -> SettingsScreen(vm) { folderPicker.launch(null) }
                        Screen.Equalizer -> EqualizerScreen()
                        Screen.Player -> PlayerScreen(vm)
                        is Screen.AlbumScreen -> AlbumScreen(vm, s.key)
                        is Screen.PlaylistScreen -> PlaylistScreen(vm, s.id)
                    }
                }
            }
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
        Screen.Player -> "Сейчас играет"
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
            selected = vm.tab == 0, onClick = { vm.tab = 0 },
            icon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null) }, label = { Text("Плейлисты") },
        )
        NavigationBarItem(
            selected = vm.tab == 1, onClick = { vm.tab = 1 },
            icon = { Icon(Icons.Default.Album, null) }, label = { Text("Альбомы") },
        )
        NavigationBarItem(
            selected = vm.tab == 2, onClick = { vm.tab = 2 },
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
                    slideInHorizontally(tween(300)) { it * dir / 3 } + fadeIn(tween(300)),
                    slideOutHorizontally(tween(300)) { -it * dir / 3 } + fadeOut(tween(150)),
                )
            },
            label = "tabs",
        ) { tab ->
            when (tab) {
                0 -> PlaylistsTab(vm)
                1 -> AlbumsTab(vm)
                else -> TracksTab(vm)
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
                leadingContent = { Cover(a.tracks.firstOrNull()?.uri, 56.dp, sample = 4) },
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
    val color by animateColorAsState(
        if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(250),
        label = "trackColor",
    )
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        leadingContent = {
            Crossfade(targetState = current, label = "trackIcon") { isCurrent ->
                Icon(
                    if (isCurrent) Icons.Default.PlayArrow else Icons.Default.MusicNote, null,
                    tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

@Composable
private fun Cover(uri: String?, size: Dp, sample: Int = 2) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, uri) {
        value = if (uri == null) null else withContext(Dispatchers.IO) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, Uri.parse(uri))
                r.embeddedPicture?.let {
                    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                    BitmapFactory.decodeByteArray(it, 0, it.size, opts)?.asImageBitmap()
                }
            } catch (_: Exception) {
                null
            } finally {
                runCatching { r.release() }
            }
        }
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
private fun MiniPlayer(vm: PlayerViewModel, bottomInset: Boolean) {
    val t = vm.currentTrack
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { vm.push(Screen.Player) }
            .then(if (bottomInset) Modifier.windowInsetsPadding(WindowInsets.navigationBars) else Modifier),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(t?.uri, 44.dp, sample = 4)
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

@Composable
private fun PlayerScreen(vm: PlayerViewModel) {
    val t = vm.currentTrack
    var pos by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(vm.currentId, vm.isPlaying) {
        while (true) {
            pos = vm.controller?.currentPosition ?: 0L
            delay(if (vm.isPlaying) 300 else 1000)
        }
    }

    val total = (t?.durationMs ?: 0L).coerceAtLeast(1L)
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Cover(t?.uri, 280.dp, sample = 1)
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
        Slider(
            value = (dragging ?: pos.toFloat()).coerceIn(0f, total.toFloat()),
            onValueChange = { dragging = it },
            onValueChangeFinished = { dragging?.let { vm.seekTo(it.toLong()) }; dragging = null },
            valueRange = 0f..total.toFloat(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime((dragging?.toLong() ?: pos)), style = MaterialTheme.typography.bodySmall)
            Text(formatTime(t?.durationMs ?: 0L), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            val shuffleTint by animateColorAsState(
                if (vm.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "shuffleTint",
            )
            val repeatTint by animateColorAsState(
                if (vm.repeat != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "repeatTint",
            )
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
        ListItem(
            modifier = Modifier.clickable { vm.push(Screen.Equalizer) },
            leadingContent = { Icon(Icons.Default.Equalizer, null) },
            headlineContent = { Text("Эквалайзер") },
            supportingContent = { Text("Настройка частот звучания") },
            trailingContent = {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        "Скоро",
                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            },
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
// Эквалайзер (заглушка)
// =====================================================================

@Composable
private fun EqualizerScreen() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Icon(Icons.Default.Equalizer, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Эквалайзер", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Появится в одном из следующих обновлений.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        // Неактивный макет будущего эквалайзера
        Row(
            Modifier.fillMaxWidth().height(200.dp).alpha(0.4f),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            listOf("60", "230", "910", "3,6к", "14к").forEach { label ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f).width(24.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.fillMaxHeight().width(6.dp).clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                        Box(Modifier.size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
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
