package com.example.localplayer

data class Track(
    val uri: String,
    val title: String,
    val artist: String,
    val albumArtist: String,
    val album: String,
    val durationMs: Long,
    val trackNo: Int,
    val info: AudioInfo = AudioInfo(),
    /** Дата изменения файла (мс): нужна для сортировки «по дате файла». */
    val modified: Long = 0L,
    /** Когда трек впервые появился в библиотеке плеера (мс). 0 — неизвестно. */
    val added: Long = 0L,
)

data class Album(
    val key: String,
    val name: String,
    val artist: String,
    val tracks: List<Track>,
)

data class Playlist(
    val id: String,
    val name: String,
    val trackUris: List<String>,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/** Плейлист по умолчанию: его нельзя удалить или переименовать, он всегда закреплён вверху списка. */
const val FAVORITES_ID = "favorites"
const val FAVORITES_NAME = "Понравившиеся треки"

/** Тема оформления. По умолчанию — [SYSTEM]: следует за настройкой телефона. */
enum class ThemeMode(val label: String, val description: String) {
    LIGHT("Ясный", "Светлое оформление"),
    DARK("Тёмный", "Чисто чёрный фон, экономит заряд на OLED-экранах"),
    SYSTEM("Системная", "Как на телефоне: тёмная, если на телефоне включена тёмная тема"),
}

const val NO_ALBUM = "Без альбома"
const val NO_ARTIST = "Неизвестный исполнитель"

/** Способы сортировки списков. [MANUAL] — порядок, который пользователь задал перетаскиванием. */
enum class SortKey(val label: String) {
    MANUAL("Вручную"),
    NAME("По названию"),
    ARTIST("По исполнителю"),
    ALBUM("По альбому"),
    DURATION("По длительности"),
    COUNT("По числу треков"),
    FILE_DATE("По дате файла"),
    CREATED("По дате создания"),
    UPDATED("По дате изменения"),
}

data class SortState(val key: SortKey, val descending: Boolean = false)

val TRACK_SORT_KEYS = listOf(SortKey.MANUAL, SortKey.NAME, SortKey.ARTIST, SortKey.ALBUM, SortKey.DURATION, SortKey.FILE_DATE)
val ALBUM_SORT_KEYS = listOf(SortKey.MANUAL, SortKey.NAME, SortKey.ARTIST, SortKey.COUNT, SortKey.FILE_DATE)
val PLAYLIST_SORT_KEYS = listOf(SortKey.MANUAL, SortKey.NAME, SortKey.CREATED, SortKey.UPDATED, SortKey.COUNT)
