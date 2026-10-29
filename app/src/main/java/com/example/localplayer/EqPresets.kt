package com.example.localplayer

/** Готовая кривая эквалайзера: 12 значений в дБ для полос 32 Гц … 16 кГц (см. [AudioFx.BAND_FREQS]). */
data class EqPreset(val name: String, val gains: List<Float>)

val EQ_PRESETS: List<EqPreset> = listOf(
    EqPreset("Рок", listOf(4f, 3f, 2f, 1f, -1f, -2f, 0f, 2f, 3f, 4f, 4f, 3f)),
    EqPreset("Поп", listOf(-1f, 0f, 2f, 3f, 3f, 2f, 0f, -1f, -1f, 0f, 1f, 1f)),
    EqPreset("Хип-хоп", listOf(5f, 5f, 4f, 2f, 1f, -1f, -1f, 1f, 1f, 2f, 2f, 1f)),
    EqPreset("Электроника", listOf(5f, 4f, 2f, 0f, -2f, -1f, 0f, 1f, 3f, 4f, 4f, 3f)),
    EqPreset("Джаз", listOf(3f, 2f, 1f, 2f, -2f, -2f, 0f, 1f, 2f, 3f, 3f, 3f)),
    EqPreset("Классика", listOf(2f, 2f, 1f, 0f, 0f, 0f, 0f, 1f, 2f, 3f, 3f, 3f)),
    EqPreset("Метал", listOf(5f, 4f, 2f, 0f, -3f, -3f, 0f, 3f, 5f, 5f, 4f, 3f)),
    EqPreset("Акустика", listOf(3f, 3f, 2f, 1f, 1f, 0f, 1f, 2f, 2f, 3f, 2f, 1f)),
    EqPreset("Вокал", listOf(-3f, -2f, -1f, 1f, 3f, 4f, 4f, 3f, 1f, 0f, -1f, -2f)),
    EqPreset("Бас", listOf(8f, 7f, 5f, 3f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
)
