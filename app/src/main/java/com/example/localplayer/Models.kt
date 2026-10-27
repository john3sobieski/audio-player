package com.example.localplayer

data class Track(
    val uri: String,
    val title: String,
    val artist: String,
    val albumArtist: String,
    val album: String,
    val durationMs: Long,
    val trackNo: Int,
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
)

const val NO_ALBUM = "Без альбома"
const val NO_ARTIST = "Неизвестный исполнитель"
