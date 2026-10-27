package com.example.localplayer

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

sealed interface Screen {
    data object Settings : Screen
    data object Equalizer : Screen
    data object Player : Screen
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

    // ---------- Настройки звука ----------
    var monoEnabled by mutableStateOf(false)
        private set
    var normalizeEnabled by mutableStateOf(false)
        private set

    fun changeMono(value: Boolean) {
        monoEnabled = value
        AudioFx.mono = value
        AudioFx.save(getApplication())
    }

    fun changeNormalize(value: Boolean) {
        normalizeEnabled = value
        AudioFx.normalize = value
        AudioFx.save(getApplication())
    }

    // ---------- Навигация ----------
    var tab by mutableIntStateOf(0)
    val stack = mutableStateListOf<Screen>()
    val screen: Screen? get() = stack.lastOrNull()
    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }

    // ---------- Диалоги ----------
    var trackForPlaylist by mutableStateOf<Track?>(null)
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
    var shuffle by mutableStateOf(false)
        private set
    var repeat by mutableIntStateOf(Player.REPEAT_MODE_OFF)
        private set

    val currentTrack: Track? get() = currentId?.let { trackByUri[it] }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { this@PlayerViewModel.isPlaying = isPlaying }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            currentId = mediaItem?.mediaId?.takeIf { it.isNotEmpty() }
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

        playlists = library.loadPlaylists()
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
                currentId = c.currentMediaItem?.mediaId?.takeIf { it.isNotEmpty() }
                shuffle = c.shuffleModeEnabled
                repeat = c.repeatMode
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(app))
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
                tracks = library.scan(f)
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

    fun play(list: List<Track>, index: Int, shuffled: Boolean = false) {
        val c = controller ?: return
        if (list.isEmpty()) return
        c.shuffleModeEnabled = shuffled
        c.setMediaItems(list.map { it.toItem() }, index.coerceIn(0, list.lastIndex), 0L)
        c.prepare()
        c.play()
    }

    fun playShuffled(list: List<Track>) {
        if (list.isEmpty()) return
        play(list, list.indices.random(), shuffled = true)
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
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

    fun createPlaylist(name: String, first: Track? = null) {
        val clean = name.trim().ifEmpty { return }
        commit(playlists + Playlist(UUID.randomUUID().toString(), clean, listOfNotNull(first?.uri)))
    }

    fun renamePlaylist(id: String, name: String) {
        val clean = name.trim().ifEmpty { return }
        commit(playlists.map { if (it.id == id) it.copy(name = clean) else it })
    }

    fun deletePlaylist(id: String) {
        commit(playlists.filterNot { it.id == id })
    }

    fun addToPlaylist(id: String, track: Track) {
        commit(playlists.map {
            if (it.id == id && track.uri !in it.trackUris) it.copy(trackUris = it.trackUris + track.uri) else it
        })
    }

    fun removeFromPlaylist(id: String, track: Track) {
        commit(playlists.map {
            if (it.id == id) it.copy(trackUris = it.trackUris - track.uri) else it
        })
    }

    fun playlistTracks(p: Playlist): List<Track> = p.trackUris.mapNotNull { trackByUri[it] }

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
        super.onCleared()
    }
}
