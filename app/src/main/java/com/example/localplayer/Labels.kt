package com.example.localplayer

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

// Подписи, которые зависят от языка интерфейса. В данных (кэш библиотеки, файл плейлистов, ключи альбомов) лежат служебные
// значения, а на экране вместо них показывается перевод.

/** Исполнитель для показа: служебное «нет исполнителя» заменяется переводом. */
@Composable
internal fun artistText(artist: String): String =
    if (artist == NO_ARTIST) stringResource(R.string.unknown_artist) else artist

/** Альбом для показа: служебное «нет альбома» заменяется переводом. */
@Composable
internal fun albumText(album: String): String =
    if (album == NO_ALBUM) stringResource(R.string.no_album) else album

/** Название плейлиста для показа: у «Понравившихся треков» оно зависит от языка, у остальных — как ввёл пользователь. */
@Composable
internal fun Playlist.label(): String =
    if (id == FAVORITES_ID) stringResource(R.string.favorites_name) else name

/** «Очередь N». */
@Composable
internal fun PlayQueue.label(): String = stringResource(R.string.queue_name, id + 1)

/** «5 треков» с правильной формой слова для текущего языка (во всех языках приложения свои правила множественного числа). */
@Composable
internal fun tracksCount(n: Int): String =
    LocalContext.current.resources.getQuantityString(R.plurals.tracks_count, n, n)
