package com.example.localplayer

import androidx.annotation.StringRes

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
    /** Файл с текстом песни рядом с треком (.lrc или .txt с тем же именем). null — такого файла нет. */
    val lyricsUri: String? = null,
    /** Текст песни зашит в теги самого файла (ID3 USLT/SYLT, Vorbis LYRICS, M4A ©lyr). */
    val tagLyrics: Boolean = false,
) {
    /** Для этого трека найден хоть какой-то источник текста (проверяется при каждом сканировании папки). */
    val hasLyrics: Boolean get() = lyricsUri != null || tagLyrics
}

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
/** Имя, под которым плейлист хранится в файле. На экране показывается перевод `R.string.favorites_name`, а не это значение. */
const val FAVORITES_NAME = "Понравившиеся треки"

/** Количество очередей: всегда ровно четыре, их нельзя ни создать, ни удалить. */
const val QUEUE_COUNT = 4

/** Очередь прослушивания. Всего их [QUEUE_COUNT]; у каждой фиксированный номер [id] (0..3), имя «Очередь N» берётся из ресурсов. */
data class PlayQueue(
    val id: Int,
    val trackUris: List<String>,
)

/** Тема оформления. По умолчанию — [SYSTEM]: следует за настройкой телефона. */
enum class ThemeMode(@StringRes val label: Int, @StringRes val description: Int) {
    LIGHT(R.string.theme_light, R.string.theme_light_desc),
    DARK(R.string.theme_dark, R.string.theme_dark_desc),
    SYSTEM(R.string.theme_system, R.string.theme_system_desc),
}

/**
 * Служебные значения «нет альбома» / «нет исполнителя»: хранятся в кэше библиотеки и в ключах альбомов, поэтому не меняются
 * с языком. На экране вместо них показываются `R.string.no_album` и `R.string.unknown_artist`.
 */
const val NO_ALBUM = "Без альбома"
const val NO_ARTIST = "Неизвестный исполнитель"

/** Способы сортировки списков. [MANUAL] — порядок, который пользователь задал перетаскиванием. */
enum class SortKey(@StringRes val label: Int) {
    MANUAL(R.string.sort_manual),
    NAME(R.string.sort_name),
    ARTIST(R.string.sort_artist),
    ALBUM(R.string.sort_album),
    DURATION(R.string.sort_duration),
    COUNT(R.string.sort_count),
    FILE_DATE(R.string.sort_file_date),
    CREATED(R.string.sort_created),
    UPDATED(R.string.sort_updated),
}

data class SortState(val key: SortKey, val descending: Boolean = false)

val TRACK_SORT_KEYS = listOf(SortKey.MANUAL, SortKey.NAME, SortKey.ARTIST, SortKey.ALBUM, SortKey.DURATION, SortKey.FILE_DATE)
val ALBUM_SORT_KEYS = listOf(SortKey.MANUAL, SortKey.NAME, SortKey.ARTIST, SortKey.COUNT, SortKey.FILE_DATE)
val PLAYLIST_SORT_KEYS = listOf(SortKey.MANUAL, SortKey.NAME, SortKey.CREATED, SortKey.UPDATED, SortKey.COUNT)
