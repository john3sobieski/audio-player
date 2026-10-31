package com.example.localplayer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import java.text.Collator
import java.util.Locale

/**
 * Язык интерфейса. [SYSTEM] (tag = null) означает «следовать языку телефона»: если язык телефона приложением
 * не поддерживается, показывается английский (ресурсы по умолчанию).
 */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    GERMAN("de"),
    RUSSIAN("ru"),
    UKRAINIAN("uk"),
    CHINESE("zh"),
    JAPANESE("ja"),
    FRENCH("fr"),
    CZECH("cs"),
    SPANISH("es"),
    POLISH("pl");

    val locale: Locale? get() = tag?.let { Locale.forLanguageTag(it) }

    /** Название языка на языке интерфейса [ui] («Немецкий» в русском интерфейсе, «German» в английском). */
    fun displayName(ui: Locale): String = locale?.getDisplayLanguage(ui).orEmpty().capitalized(ui)

    /** Название языка на нём самом («Deutsch», «Русский», «日本語»): по нему язык находят, даже если интерфейс непонятен. */
    fun nativeName(): String = locale?.let { it.getDisplayLanguage(it).capitalized(it) }.orEmpty()

    companion object {
        /** Все языки, кроме [SYSTEM], по алфавиту (по названиям на языке интерфейса [ui]). */
        fun sortedFor(ui: Locale): List<AppLanguage> {
            val collator = Collator.getInstance(ui)
            return entries.filter { it != SYSTEM }.sortedWith { a, b -> collator.compare(a.displayName(ui), b.displayName(ui)) }
        }

        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag != null && it.tag == tag } ?: SYSTEM
    }
}

private fun String.capitalized(l: Locale): String = replaceFirstChar { it.titlecase(l) }

/**
 * Хранение и применение выбранного языка.
 *
 * Язык применяется в [MainActivity.attachBaseContext]: активность получает ресурсы с нужной локалью, поэтому
 * `stringResource` и `plurals` сами выбирают перевод. Смена языка = запись выбора + `recreate()` активности.
 */
object AppLocale {

    private const val PREFS = "settings"
    private const val KEY = "app_language"

    fun saved(ctx: Context): AppLanguage =
        AppLanguage.fromTag(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))

    fun save(ctx: Context, lang: AppLanguage) {
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (lang.tag == null) remove(KEY) else putString(KEY, lang.tag)
        }.apply()
    }

    /**
     * Контекст с выбранным языком. Для [AppLanguage.SYSTEM] возвращает [base] как есть, но возвращает
     * `Locale.getDefault()` к системному значению (иначе после выбора языка вручную форматирование чисел
     * оставалось бы в прежней локали).
     */
    fun wrap(base: Context): Context {
        val locale = saved(base).locale
        if (locale == null) {
            Locale.setDefault(base.resources.configuration.locales[0])
            return base
        }
        Locale.setDefault(locale)
        return localized(base)
    }

    /** Контекст с выбранным языком без побочных эффектов (не трогает `Locale.getDefault()`). */
    fun localized(base: Context): Context {
        val locale = saved(base).locale ?: return base
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(locale)
        return base.createConfigurationContext(cfg)
    }

    /** Строка на выбранном языке для кода вне интерфейса (сервис, уведомления). */
    fun string(ctx: Context, id: Int): String = localized(ctx).getString(id)
}

/** Находит активность за обёртками контекста Compose. */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
