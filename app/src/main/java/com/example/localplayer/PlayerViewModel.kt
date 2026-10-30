package com.example.localplayer

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.roundToInt

sealed interface Screen {
    data object Settings : Screen
    data object Equalizer : Screen
    /** Экран эквалайзера, открытый из плеера: без переключения режимов. */
    data object PlayerEqualizer : Screen
    data object Player : Screen
    /** Окно текста песни: открывается из плеера, а сам плеер при этом сжимается до мини-плеера внизу. */
    data object Lyrics : Screen
    data class AlbumScreen(val key: String) : Screen
    data class PlaylistScreen(val id: String) : Screen
}

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val library = Library(app)

    // ---------- Данные ----------
    var folder by mutableStateOf<Uri?>(null)
        private set
    var tracks by mutableStateOf<List<Track>>(emptyList())
        private set
    var playlists by mutableStateOf<List<Playlist>>(emptyList())
        private set
    var scanning by mutableStateOf(false)
        private set
    /** Четыре очереди (всегда ровно [QUEUE_COUNT]). */
    var queues by mutableStateOf(library.loadQueues())
        private set
    /** Какая очередь открыта в разделе «Очереди» (0..3). */
    var selectedQueue by mutableIntStateOf(library.loadSelectedQueue())
        private set

    val trackByUri by derivedStateOf { tracks.associateBy { it.uri } }

    /** Альбомы строятся по тегам файлов (album + album artist). */
    val albums by derivedStateOf {
        tracks.groupBy { it.album to it.albumArtist }
            .map { (k, list) ->
                Album(
                    key = "${k.first}\u0000${k.second}",
                    name = k.first,
                    artist = k.second,
                    tracks = list.sortedWith(compareBy({ it.trackNo.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }, { it.title.lowercase() })),
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    // ---------- Сортировка и ручной порядок ----------

    var trackSort by mutableStateOf(library.loadSort("tracks", SortState(SortKey.NAME), TRACK_SORT_KEYS))
        private set
    var albumSort by mutableStateOf(library.loadSort("albums", SortState(SortKey.NAME), ALBUM_SORT_KEYS))
        private set
    var playlistSort by mutableStateOf(library.loadSort("playlists", SortState(SortKey.MANUAL), PLAYLIST_SORT_KEYS))
        private set

    private var trackOrder by mutableStateOf(library.loadOrder("tracks"))
    private var albumOrder by mutableStateOf(library.loadOrder("albums"))
    private var playlistOrder by mutableStateOf(library.loadOrder("playlists"))

    /** Универсальная сортировка: ручной порядок или компаратор, затем (при необходимости) разворот. */
    private fun <T> applySort(
        base: List<T>,
        sort: SortState,
        manual: List<String>,
        id: (T) -> String,
        comparator: (SortKey) -> Comparator<T>,
    ): List<T> {
        val asc = if (sort.key == SortKey.MANUAL) {
            val pos = HashMap<String, Int>(manual.size * 2)
            manual.forEachIndexed { i, k -> pos[k] = i }
            // Новые элементы, которых нет в сохранённом порядке, остаются в конце в исходном порядке
            base.sortedBy { pos[id(it)] ?: Int.MAX_VALUE }
        } else {
            base.sortedWith(comparator(sort.key))
        }
        return if (sort.descending) asc.asReversed() else asc
    }

    val sortedTracks by derivedStateOf {
        applySort(tracks, trackSort, trackOrder, { it.uri }) { key ->
            when (key) {
                SortKey.ARTIST -> compareBy<Track>({ it.artist.lowercase() }, { it.title.lowercase() })
                SortKey.ALBUM -> compareBy<Track>(
                    { it.album.lowercase() },
                    { it.trackNo.takeIf { n -> n > 0 } ?: Int.MAX_VALUE },
                    { it.title.lowercase() },
                )
                SortKey.DURATION -> compareBy<Track>({ it.durationMs }, { it.title.lowercase() })
                SortKey.FILE_DATE -> compareBy<Track>({ it.modified }, { it.title.lowercase() })
                else -> compareBy<Track> { it.title.lowercase() }
            }
        }
    }

    val sortedAlbums by derivedStateOf {
        // «Без альбома» всегда в самом низу, при любой сортировке и направлении
        val (noAlbum, regular) = applySort(albums, albumSort, albumOrder, { it.key }) { key ->
            when (key) {
                SortKey.ARTIST -> compareBy<Album>({ it.artist.lowercase() }, { it.name.lowercase() })
                SortKey.COUNT -> compareBy<Album>({ it.tracks.size }, { it.name.lowercase() })
                SortKey.FILE_DATE -> compareBy<Album>({ a -> a.tracks.maxOfOrNull { it.modified } ?: 0L }, { it.name.lowercase() })
                else -> compareBy<Album> { it.name.lowercase() }
            }
        }.partition { it.name == NO_ALBUM }
        regular + noAlbum
    }

    val sortedPlaylists by derivedStateOf {
        // «Понравившиеся треки» всегда в самом верху, при любой сортировке и направлении
        val (fav, regular) = applySort(playlists, playlistSort, playlistOrder, { it.id }) { key ->
            when (key) {
                SortKey.CREATED -> compareBy<Playlist>({ it.createdAt }, { it.name.lowercase() })
                SortKey.UPDATED -> compareBy<Playlist>({ it.updatedAt }, { it.name.lowercase() })
                SortKey.COUNT -> compareBy<Playlist>({ it.trackUris.size }, { it.name.lowercase() })
                else -> compareBy<Playlist> { it.name.lowercase() }
            }
        }.partition { it.id == FAVORITES_ID }
        fav + regular
    }

    /** URI треков, которые есть хотя бы в одном плейлисте (для закрашенного сердечка в плеере). */
    val savedUris by derivedStateOf {
        HashSet<String>().also { set -> playlists.forEach { set.addAll(it.trackUris) } }
    }

    fun changeTrackSort(s: SortState) { trackSort = s; library.saveSort("tracks", s) }
    fun changeAlbumSort(s: SortState) { albumSort = s; library.saveSort("albums", s) }
    fun changePlaylistSort(s: SortState) { playlistSort = s; library.saveSort("playlists", s) }

    /**
     * Пользователь перетащил элемент: показанный порядок становится ручным, а сортировка переключается на «Вручную».
     * При убывающем направлении ручной порядок хранится развёрнутым, чтобы список не «перевернулся».
     */
    fun commitTrackOrder(shown: List<String>) {
        val manual = if (trackSort.descending) shown.asReversed() else shown
        trackOrder = manual.toList()
        viewModelScope.launch(Dispatchers.IO) { library.saveOrder("tracks", manual.toList()) }
        changeTrackSort(trackSort.copy(key = SortKey.MANUAL))
    }

    fun commitAlbumOrder(shown: List<String>) {
        val manual = if (albumSort.descending) shown.asReversed() else shown
        albumOrder = manual.toList()
        viewModelScope.launch(Dispatchers.IO) { library.saveOrder("albums", manual.toList()) }
        changeAlbumSort(albumSort.copy(key = SortKey.MANUAL))
    }

    fun commitPlaylistOrder(shown: List<String>) {
        val manual = if (playlistSort.descending) shown.asReversed() else shown
        playlistOrder = manual.toList()
        viewModelScope.launch(Dispatchers.IO) { library.saveOrder("playlists", manual.toList()) }
        changePlaylistSort(playlistSort.copy(key = SortKey.MANUAL))
    }

    // ---------- Тема ----------

    var themeMode by mutableStateOf(library.loadTheme())
        private set

    fun changeTheme(mode: ThemeMode) {
        themeMode = mode
        library.saveTheme(mode)
    }

    // ---------- Настройки звука ----------
    var monoEnabled by mutableStateOf(false)
        private set
    var normalizeEnabled by mutableStateOf(false)
        private set

    fun changeMono(value: Boolean) {
        monoEnabled = value
        AudioFx.mono = value
        AudioFx.persistSettings()
    }

    fun changeNormalize(value: Boolean) {
        normalizeEnabled = value
        AudioFx.normalize = value
        AudioFx.persistSettings()
    }

    // ---------- Басы, требл, subsonic-фильтр и эквалайзер ----------
    // Все четыре настройки независимы друг от друга и работают одновременно.

    var eqMode by mutableStateOf(EqMode.OFF)
        private set
    var bassEnabled by mutableStateOf(false)
        private set
    var bassDb by mutableFloatStateOf(6f)
        private set
    var trebleEnabled by mutableStateOf(false)
        private set
    var trebleDb by mutableFloatStateOf(6f)
        private set
    var subEnabled by mutableStateOf(false)
        private set
    var subLevel by mutableFloatStateOf(50f)
        private set
    var globalGains by mutableStateOf(List(AudioFx.BANDS) { 0f })
        private set
    var speedEnabled by mutableStateOf(false)
        private set
    /** Положение ползунка скорости (−2...+2), см. [AudioFx.SPEED_MIN]. */
    var speedValue by mutableFloatStateOf(0f)
        private set

    /** Счётчик, который увеличивается при любом изменении кривых треков или смене текущего трека. */
    private var trackRev by mutableIntStateOf(0)

    /** В настройках ползунки активны только в режиме «для всех треков». В режиме «для каждого трека» они заблокированы. */
    val canEditInSettings: Boolean
        get() = eqMode == EqMode.GLOBAL

    /** Кривая, которую показывает эквалайзер в плеере: общая или кривая текущего трека, в зависимости от режима. */
    val playerGains: List<Float>
        get() = when (eqMode) {
            EqMode.PER_TRACK -> trackRev.let { AudioFx.trackGainsFor(currentId).toList() }
            // Трек исключён из общей настройки: показываем и меняем его собственную кривую
            EqMode.GLOBAL ->
                if (currentExcludesGlobal) AudioFx.trackGainsFor(currentId).toList() else globalGains
            else -> globalGains
        }

    /** Для играющего трека включено «не применять общий эквалайзер». */
    val currentExcludesGlobal: Boolean
        get() = trackRev.let { AudioFx.isGlobalExcluded(currentId) }

    fun changeGlobalExcluded(value: Boolean) {
        val id = currentId ?: return
        AudioFx.setGlobalExcluded(id, value)
        AudioFx.persistSettings()
        trackRev++
    }

    /** В плеере ползунки активны в обоих режимах (для режима «для каждого трека» нужен играющий трек). */
    val canEditInPlayer: Boolean
        get() = when (eqMode) {
            EqMode.OFF -> false
            EqMode.GLOBAL -> true
            EqMode.PER_TRACK -> currentId != null
        }

    val customTrackCount: Int get() = trackRev.let { AudioFx.customTrackCount() }

    fun changeEqMode(mode: EqMode) {
        eqMode = mode
        AudioFx.eqMode = mode
        AudioFx.refresh()
        AudioFx.persistSettings()
        trackRev++
    }

    fun changeBassEnabled(value: Boolean) {
        bassEnabled = value
        AudioFx.bassEnabled = value
        AudioFx.refresh()
        AudioFx.persistSettings()
    }

    /** Меняется «вживую» во время перетаскивания ползунка; сохраняется в [commitSound]. */
    fun changeBassDb(value: Float) {
        bassDb = value
        AudioFx.bassLevel = value
        AudioFx.refresh()
    }

    fun changeTrebleEnabled(value: Boolean) {
        trebleEnabled = value
        AudioFx.trebleEnabled = value
        AudioFx.refresh()
        AudioFx.persistSettings()
    }

    fun changeTrebleDb(value: Float) {
        trebleDb = value
        AudioFx.trebleLevel = value
        AudioFx.refresh()
    }

    fun changeSubEnabled(value: Boolean) {
        subEnabled = value
        AudioFx.subEnabled = value
        AudioFx.refresh()
        AudioFx.persistSettings()
    }

    fun changeSubLevel(value: Float) {
        subLevel = value
        AudioFx.subLevel = value
        AudioFx.refresh()
    }

    private fun applySpeed() {
        controller?.setPlaybackParameters(PlaybackParameters(AudioFx.effectiveSpeed()))
    }

    fun changeSpeedEnabled(value: Boolean) {
        speedEnabled = value
        AudioFx.speedEnabled = value
        applySpeed()
        AudioFx.persistSettings()
    }

    /** Скорость от −3x (в 3 раза медленнее) до 3x (в 3 раза быстрее) с шагом 0,1. Меняется «вживую» при перетаскивании ползунка; сохраняется в [commitSound]. */
    fun changeSpeedValue(value: Float) {
        val v = ((value * 10f).roundToInt() / 10f).coerceIn(AudioFx.SPEED_MIN, AudioFx.SPEED_MAX)
        speedValue = v
        AudioFx.speedLevel = v
        if (speedEnabled) applySpeed()
    }

    fun setEqBand(band: Int, db: Float, fromPlayer: Boolean) {
        val v = db.coerceIn(-AudioFx.EQ_RANGE_DB, AudioFx.EQ_RANGE_DB)
        when (eqMode) {
            EqMode.GLOBAL -> {
                val id = currentId
                if (fromPlayer && id != null && AudioFx.isGlobalExcluded(id)) {
                    // Для этого трека общая настройка отключена: ползунки меняют его собственную кривую
                    AudioFx.setTrackBand(id, band, v)
                    trackRev++
                } else {
                    AudioFx.setGlobalBand(band, v)
                    globalGains = AudioFx.globalGains.toList()
                }
            }
            EqMode.PER_TRACK -> {
                if (!fromPlayer) return // кривые отдельных треков меняются только из плеера
                val id = currentId ?: return
                AudioFx.setTrackBand(id, band, v)
                trackRev++
            }
            EqMode.OFF -> Unit
        }
    }

    /** Применяет пресет по тем же правилам, что и ручное движение ползунков (см. [setEqBand]). */
    fun applyEqPreset(preset: EqPreset, fromPlayer: Boolean) {
        val g = FloatArray(AudioFx.BANDS) { preset.gains[it].coerceIn(-AudioFx.EQ_RANGE_DB, AudioFx.EQ_RANGE_DB) }
        when (eqMode) {
            EqMode.GLOBAL -> {
                val id = currentId
                if (fromPlayer && id != null && AudioFx.isGlobalExcluded(id)) {
                    AudioFx.setTrackGains(id, g)
                    trackRev++
                } else {
                    AudioFx.setGlobalGains(g)
                    globalGains = AudioFx.globalGains.toList()
                }
            }
            EqMode.PER_TRACK -> {
                if (!fromPlayer) return // кривые отдельных треков меняются только из плеера
                val id = currentId ?: return
                AudioFx.setTrackGains(id, g)
                trackRev++
            }
            EqMode.OFF -> return
        }
        commitSound()
    }

    fun resetEq(fromPlayer: Boolean) {
        when (eqMode) {
            EqMode.GLOBAL -> {
                val id = currentId
                if (fromPlayer && id != null && AudioFx.isGlobalExcluded(id)) {
                    AudioFx.resetTrack(id)
                    trackRev++
                } else {
                    AudioFx.resetGlobal()
                    globalGains = AudioFx.globalGains.toList()
                }
            }
            EqMode.PER_TRACK -> {
                if (!fromPlayer) return
                currentId?.let {
                    AudioFx.resetTrack(it)
                    trackRev++
                }
            }
            EqMode.OFF -> Unit
        }
        commitSound()
    }

    /** Записывает настройки на диск (вызывается, когда пользователь отпустил ползунок). */
    fun commitSound() {
        AudioFx.persistSettings()
        AudioFx.persistTracks()
    }

    /** Эквалайзер из плеера открывается поверх плеера; кнопка «Назад» возвращает в плеер. */
    fun openPlayerEqualizer() {
        if (screen != Screen.PlayerEqualizer) push(Screen.PlayerEqualizer)
    }

    /** Текст песни открывается поверх плеера; «Назад» (или нажатие на мини-плеер) возвращает в плеер. */
    fun openLyrics() {
        if (screen != Screen.Lyrics) push(Screen.Lyrics)
    }

    // ---------- Навигация ----------
    var tab by mutableIntStateOf(0)
    // ---------- Поиск (на вкладках библиотеки) ----------
    var searchOpen by mutableStateOf(false)
        private set
    var searchQuery by mutableStateOf("")

    /** Текущий запрос; пустой, если поиск закрыт. */
    val activeQuery: String get() = if (searchOpen) searchQuery.trim() else ""

    fun openSearch() { searchOpen = true }
    fun closeSearch() { searchOpen = false; searchQuery = "" }

    val stack = mutableStateListOf<Screen>()
    val screen: Screen? get() = stack.lastOrNull()
    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }

    /** Переход на вкладку библиотеки из любого экрана (нижняя навигация доступна везде). */
    fun openTab(index: Int) {
        if (tab != index) searchQuery = "" // у каждой вкладки свой поиск
        tab = index
        stack.clear()
    }

    fun openPlayer() {
        if (screen != Screen.Player) push(Screen.Player)
    }

    // ---------- Диалоги ----------
    var trackForPlaylist by mutableStateOf<Track?>(null)
    /** Трек, для которого открыто окно выбора очереди. */
    var trackForQueue by mutableStateOf<Track?>(null)
    var newPlaylistDialog by mutableStateOf(false)
    var newPlaylistFirstTrack by mutableStateOf<Track?>(null)
    var renameTarget by mutableStateOf<Playlist?>(null)

    // ---------- Плеер ----------
    var controller: MediaController? = null
        private set
    var currentId by mutableStateOf<String?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set

    /**
     * Что показывать на кнопке Play/Pause. Меняется сразу по нажатию, не дожидаясь, пока плавное затухание
     * реально остановит звук (само затухание остаётся); с реальным состоянием плеера синхронизируется по событиям.
     */
    var uiPlaying by mutableStateOf(false)
        private set

    private var uiTarget: Boolean? = null
    private var uiTargetJob: Job? = null

    /**
     * Кнопка меняется мгновенно по нажатию. MediaController сначала сам «оптимистично» ставит паузу, а затем сессия
     * присылает настоящее состояние (звук ещё затухает, isPlaying = true), поэтому события плеера в течение короткого
     * окна после нажатия игнорируются, и только потом кнопка снова следует за плеером.
     */
    private fun markUiPlaying(value: Boolean) {
        uiPlaying = value
        uiTarget = value
        uiTargetJob?.cancel()
        uiTargetJob = viewModelScope.launch {
            kotlinx.coroutines.delay(if (value) 2000L else 800L)
            uiTarget = null
            val c = controller
            uiPlaying = isPlaying || (c != null && c.playWhenReady && c.playbackState == Player.STATE_BUFFERING)
        }
    }

    var shuffle by mutableStateOf(false)
        private set
    var repeat by mutableIntStateOf(Player.REPEAT_MODE_OFF)
        private set

    val currentTrack: Track? get() = currentId?.let { trackByUri[it] }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@PlayerViewModel.isPlaying = isPlaying
            if (uiTarget == null) uiPlaying = isPlaying
        }
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            uiPlaying = isPlaying
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) uiPlaying = isPlaying
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            currentId = mediaItem?.mediaId?.takeIf { it.isNotEmpty() }
            AudioFx.setCurrentUri(currentId)
            trackRev++
        }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { shuffle = shuffleModeEnabled }
        override fun onRepeatModeChanged(repeatMode: Int) { repeat = repeatMode }
    }

    private var scanJob: Job? = null
    private var scanId = 0

    init {
        AudioFx.load(app)
        monoEnabled = AudioFx.mono
        normalizeEnabled = AudioFx.normalize
        eqMode = AudioFx.eqMode
        bassEnabled = AudioFx.bassEnabled
        bassDb = AudioFx.bassLevel
        trebleEnabled = AudioFx.trebleEnabled
        trebleDb = AudioFx.trebleLevel
        subEnabled = AudioFx.subEnabled
        subLevel = AudioFx.subLevel
        speedEnabled = AudioFx.speedEnabled
        speedValue = AudioFx.speedLevel
        globalGains = AudioFx.globalGains.toList()

        val loaded = library.loadPlaylists()
        playlists = withFavorites(loaded)
        if (playlists.size != loaded.size) library.savePlaylists(playlists)
        connectPlayer()

        val saved = library.savedFolder()
        val cr = app.contentResolver
        if (saved != null && cr.persistedUriPermissions.any { it.uri == saved && it.isReadPermission }) {
            folder = saved
            // Сразу показываем результат прошлого сканирования; актуальный скан запускает MainActivity.onStart()
            viewModelScope.launch {
                val cached = withContext(Dispatchers.IO) { library.cachedTracks() }
                if (tracks.isEmpty()) tracks = cached
            }
        } else if (saved != null) {
            library.saveFolder(null) // доступ потерян — просим выбрать заново
        }
    }

    private fun connectPlayer() {
        val app = getApplication<Application>()
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                controller = c
                c.addListener(listener)
                isPlaying = c.isPlaying
                uiPlaying = c.isPlaying
                currentId = c.currentMediaItem?.mediaId?.takeIf { it.isNotEmpty() }
                AudioFx.setCurrentUri(currentId)
                trackRev++
                shuffle = c.shuffleModeEnabled
                repeat = c.repeatMode
                applySpeed()
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(app))
    }

    // ---------- Кэш и сброс настроек ----------

    /** Размер дискового кэша обложек (обновляется при открытии настроек и после очистки). */
    var cacheBytes by mutableLongStateOf(0L)
        private set

    fun refreshCacheSize() {
        viewModelScope.launch {
            cacheBytes = withContext(Dispatchers.IO) { CoverStore.sizeBytes(getApplication<Application>()) }
        }
    }

    fun clearAppCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { CoverStore.clear(getApplication<Application>()) }
            cacheBytes = withContext(Dispatchers.IO) { CoverStore.sizeBytes(getApplication<Application>()) }
        }
    }

    /** Заводские настройки: звук, эквалайзеры, сортировки. Плейлисты и выбранная папка остаются. */
    fun factoryReset(clearCache: Boolean) {
        AudioFx.resetAll()
        monoEnabled = false
        normalizeEnabled = false
        eqMode = AudioFx.eqMode
        bassEnabled = AudioFx.bassEnabled
        bassDb = AudioFx.bassLevel
        trebleEnabled = AudioFx.trebleEnabled
        trebleDb = AudioFx.trebleLevel
        subEnabled = AudioFx.subEnabled
        subLevel = AudioFx.subLevel
        speedEnabled = AudioFx.speedEnabled
        speedValue = AudioFx.speedLevel
        globalGains = AudioFx.globalGains.toList()
        applySpeed()
        trackRev++

        viewModelScope.launch(Dispatchers.IO) { library.resetViewSettings() }
        trackSort = SortState(SortKey.NAME)
        albumSort = SortState(SortKey.NAME)
        playlistSort = SortState(SortKey.MANUAL)
        trackOrder = emptyList()
        albumOrder = emptyList()
        playlistOrder = emptyList()

        if (clearCache) clearAppCache()
    }

    // ---------- Папка и сканирование ----------

    fun selectFolder(uri: Uri) {
        val cr = getApplication<Application>().contentResolver
        runCatching { cr.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        folder?.takeIf { it != uri }?.let { old ->
            runCatching { cr.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        controller?.apply { stop(); clearMediaItems() }
        currentId = null
        playingSource = null
        library.saveFolder(uri)
        folder = uri
        tracks = emptyList()
        rescan(force = true)
    }

    /** Вызывается при каждом входе в приложение: подхватывает добавленные и удалённые файлы. */
    fun refresh() {
        if (folder != null) rescan()
    }

    fun rescan(force: Boolean = false) {
        val f = folder ?: return
        if (force) library.clearCache()
        scanJob?.cancel()
        val id = ++scanId
        scanJob = viewModelScope.launch {
            scanning = true
            try {
                if (force) withContext(Dispatchers.IO) { CoverStore.clear(getApplication<Application>()) }
                tracks = library.scan(f)
                Lyrics.clearCache() // .lrc/.txt могли измениться: тексты перечитаются при следующем открытии
                // Чистим дисковый кэш обложек от треков, которых больше нет в библиотеке
                val uris = tracks.map { it.uri }
                if (uris.isNotEmpty()) withContext(Dispatchers.IO) { CoverStore.prune(getApplication<Application>(), uris) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // оставляем прежний список
            } finally {
                if (id == scanId) scanning = false
            }
        }
    }

    // ---------- Воспроизведение ----------

    private fun Track.toItem() = MediaItem.Builder()
        .setMediaId(uri)
        .setUri(Uri.parse(uri))
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album).build(),
        )
        .build()

    /**
     * Откуда сейчас играет очередь: ключ экрана альбома/плейлиста (например, `album:…`, `playlist:…`) или null,
     * если трек запущен из общего списка. По нему кнопка «Играть» на экране списка понимает, что играет именно он.
     */
    var playingSource by mutableStateOf<String?>(null)
        private set

    fun play(list: List<Track>, index: Int, shuffled: Boolean = false, source: String? = null) {
        val c = controller ?: return
        if (list.isEmpty()) return
        playingSource = source
        c.shuffleModeEnabled = shuffled
        c.setMediaItems(list.map { it.toItem() }, index.coerceIn(0, list.lastIndex), 0L)
        c.prepare()
        c.play()
        markUiPlaying(true)
    }

    fun playShuffled(list: List<Track>, source: String? = null) {
        if (list.isEmpty()) return
        play(list, list.indices.random(), shuffled = true, source = source)
    }

    /**
     * Кнопка «Играть / Пауза» на экране альбома или плейлиста: если очередь уже загружена из этого списка,
     * ставит на паузу или продолжает; иначе запускает список с начала.
     */
    fun toggleList(list: List<Track>, source: String) {
        val c = controller ?: return
        if (playingSource == source && c.mediaItemCount > 0 && c.playbackState != Player.STATE_ENDED) togglePlay()
        else play(list, 0, source = source)
    }

    fun togglePlay() {
        val c = controller ?: return
        // Ориентируемся на то, что видит пользователь: во время затухания c.isPlaying ещё true
        if (uiPlaying) {
            markUiPlaying(false)
            c.pause()
        } else {
            markUiPlaying(true)
            c.play()
        }
    }
    fun next() { controller?.seekToNextMediaItem() }
    fun prev() {
        val c = controller ?: return
        if (c.currentPosition > 3000) c.seekTo(0) else c.seekToPreviousMediaItem()
    }
    fun seekTo(ms: Long) { controller?.seekTo(ms) }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    // ---------- Плейлисты ----------

    private fun commit(list: List<Playlist>) {
        playlists = list
        library.savePlaylists(list)
    }

    /** Гарантирует, что плейлист «Понравившиеся треки» существует. */
    private fun withFavorites(list: List<Playlist>): List<Playlist> {
        if (list.any { it.id == FAVORITES_ID }) return list
        val now = System.currentTimeMillis()
        return listOf(Playlist(FAVORITES_ID, FAVORITES_NAME, emptyList(), now, now)) + list
    }

    /** Сердечко в плеере (как «плюс» в Spotify): открывает окно выбора плейлистов для этого трека. */
    fun openAddToPlaylist(track: Track) { trackForPlaylist = track }

    /** В окне выбора плейлистов: нажатие добавляет трек в плейлист или, если он там уже есть, убирает. */
    fun togglePlaylistMembership(id: String, track: Track) {
        val p = playlists.firstOrNull { it.id == id } ?: return
        if (track.uri in p.trackUris) removeFromPlaylist(id, track) else addToPlaylist(id, track)
    }

    /** Число каналов играющего файла (по данным плеера); 0 — неизвестно. */
    fun currentChannelCount(): Int = runCatching {
        controller?.currentTracks?.groups
            ?.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
            ?.getTrackFormat(0)?.channelCount
    }.getOrNull()?.takeIf { it > 0 } ?: 0

    fun createPlaylist(name: String, first: Track? = null) {
        val clean = name.trim().ifEmpty { return }
        val now = System.currentTimeMillis()
        commit(playlists + Playlist(UUID.randomUUID().toString(), clean, listOfNotNull(first?.uri), now, now))
    }

    fun renamePlaylist(id: String, name: String) {
        if (id == FAVORITES_ID) return
        val clean = name.trim().ifEmpty { return }
        commit(playlists.map { if (it.id == id) it.copy(name = clean, updatedAt = System.currentTimeMillis()) else it })
    }

    fun deletePlaylist(id: String) {
        if (id == FAVORITES_ID) return
        commit(playlists.filterNot { it.id == id })
    }

    fun addToPlaylist(id: String, track: Track) {
        commit(playlists.map {
            if (it.id == id && track.uri !in it.trackUris) {
                it.copy(trackUris = it.trackUris + track.uri, updatedAt = System.currentTimeMillis())
            } else it
        })
    }

    fun removeFromPlaylist(id: String, track: Track) {
        commit(playlists.map {
            if (it.id == id) it.copy(trackUris = it.trackUris - track.uri, updatedAt = System.currentTimeMillis()) else it
        })
    }

    /** Новый порядок треков внутри плейлиста (после перетаскивания). Недоступные сейчас файлы остаются в конце. */
    fun reorderPlaylistTracks(id: String, shown: List<String>) {
        commit(playlists.map {
            if (it.id != id) it
            else {
                val shownSet = shown.toHashSet()
                it.copy(
                    trackUris = shown + it.trackUris.filter { u -> u !in shownSet },
                    updatedAt = System.currentTimeMillis(),
                )
            }
        })
    }

    fun playlistTracks(p: Playlist): List<Track> = p.trackUris.mapNotNull { trackByUri[it] }

    // ---------- Очереди ----------

    private fun commitQueues(list: List<PlayQueue>) {
        queues = list
        library.saveQueues(list)
    }

    private fun updateQueue(id: Int, change: (List<String>) -> List<String>) {
        commitQueues(queues.map { if (it.id == id) it.copy(trackUris = change(it.trackUris)) else it })
    }

    fun selectQueue(index: Int) {
        val i = index.coerceIn(0, QUEUE_COUNT - 1)
        selectedQueue = i
        library.saveSelectedQueue(i)
    }

    fun queueTracks(q: PlayQueue): List<Track> = q.trackUris.mapNotNull { trackByUri[it] }

    /** URI треков, которые есть хотя бы в одной очереди (подсветка кнопки очередей в плеере). */
    val queuedUris by derivedStateOf {
        HashSet<String>().also { set -> queues.forEach { set.addAll(it.trackUris) } }
    }

    /** В окне выбора очереди: нажатие добавляет трек в конец очереди или, если он там уже есть, убирает. */
    fun toggleQueueMembership(id: Int, track: Track) {
        val q = queues.firstOrNull { it.id == id } ?: return
        if (track.uri in q.trackUris) removeFromQueue(id, track) else addToQueue(id, track)
    }

    fun addToQueue(id: Int, track: Track) {
        updateQueue(id) { if (track.uri in it) it else it + track.uri }
    }

    fun removeFromQueue(id: Int, track: Track) {
        updateQueue(id) { it - track.uri }
    }

    /**
     * Сдвигает трек на одну позицию вверх ([delta] = −1) или вниз (+1) относительно ближайшего соседа, которого видно в списке
     * (файлы, пропавшие из библиотеки, не мешают).
     */
    fun moveInQueue(id: Int, track: Track, delta: Int) {
        updateQueue(id) { uris ->
            val visible = uris.filter { it in trackByUri }
            val vi = visible.indexOf(track.uri)
            val neighbour = visible.getOrNull(vi + delta)
            if (vi < 0 || neighbour == null) uris
            else uris.toMutableList().apply {
                val from = indexOf(track.uri)
                removeAt(from)
                val at = indexOf(neighbour)
                add(if (delta > 0) at + 1 else at, track.uri)
            }
        }
    }

    /** Новый порядок треков очереди после перетаскивания. Недоступные сейчас файлы остаются в конце. */
    fun reorderQueueTracks(id: Int, shown: List<String>) {
        updateQueue(id) { uris ->
            val shownSet = shown.toHashSet()
            shown + uris.filter { it !in shownSet }
        }
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
        super.onCleared()
    }
}
