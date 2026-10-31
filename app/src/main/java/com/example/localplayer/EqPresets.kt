package com.example.localplayer

import androidx.annotation.StringRes

/** Готовая кривая эквалайзера: 12 значений в дБ для полос 32 Гц … 16 кГц (см. [AudioFx.BAND_FREQS]). */
data class EqPreset(@StringRes val name: Int, val gains: List<Float>)

val EQ_PRESETS: List<EqPreset> = listOf(
    EqPreset(R.string.preset_rock, listOf(4f, 3f, 2f, 1f, -1f, -2f, 0f, 2f, 3f, 4f, 4f, 3f)),
    EqPreset(R.string.preset_pop, listOf(-1f, 0f, 2f, 3f, 3f, 2f, 0f, -1f, -1f, 0f, 1f, 1f)),
    EqPreset(R.string.preset_hiphop, listOf(5f, 5f, 4f, 2f, 1f, -1f, -1f, 1f, 1f, 2f, 2f, 1f)),
    EqPreset(R.string.preset_electronic, listOf(5f, 4f, 2f, 0f, -2f, -1f, 0f, 1f, 3f, 4f, 4f, 3f)),
    EqPreset(R.string.preset_jazz, listOf(3f, 2f, 1f, 2f, -2f, -2f, 0f, 1f, 2f, 3f, 3f, 3f)),
    EqPreset(R.string.preset_classical, listOf(2f, 2f, 1f, 0f, 0f, 0f, 0f, 1f, 2f, 3f, 3f, 3f)),
    EqPreset(R.string.preset_metal, listOf(5f, 4f, 2f, 0f, -3f, -3f, 0f, 3f, 5f, 5f, 4f, 3f)),
    EqPreset(R.string.preset_acoustic, listOf(3f, 3f, 2f, 1f, 1f, 0f, 1f, 2f, 2f, 3f, 2f, 1f)),
    EqPreset(R.string.preset_vocal, listOf(-3f, -2f, -1f, 1f, 3f, 4f, 4f, 3f, 1f, 0f, -1f, -2f)),
    EqPreset(R.string.preset_bass, listOf(8f, 7f, 5f, 3f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
)
