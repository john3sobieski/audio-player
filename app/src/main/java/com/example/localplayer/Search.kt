package com.example.localplayer

private fun norm(s: String): String = s.lowercase().replace('ё', 'е')

/**
 * Поиск по части слова и по целому слову без учёта регистра и «ё/е».
 * Запрос из нескольких слов находит элемент, в котором есть все слова (в любом порядке и в любом поле).
 */
fun matchesQuery(query: String, vararg fields: String): Boolean {
    val tokens = norm(query).split(' ', '\t').filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return true
    val hay = fields.joinToString(" ") { norm(it) }
    return tokens.all { it in hay }
}

fun Track.matches(q: String) = matchesQuery(
    q, title,
    artist.takeUnless { it == NO_ARTIST }.orEmpty(),
    album.takeUnless { it == NO_ALBUM }.orEmpty(),
)

fun Album.matches(q: String) = matchesQuery(q, name.takeUnless { it == NO_ALBUM }.orEmpty(), artist.takeUnless { it == NO_ARTIST }.orEmpty())

/** [label] — название так, как оно показано на экране (у «Понравившихся» оно зависит от языка). */
fun Playlist.matches(q: String, label: String = name) = matchesQuery(q, label)
