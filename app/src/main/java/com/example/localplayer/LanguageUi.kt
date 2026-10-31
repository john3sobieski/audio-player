package com.example.localplayer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * Язык, на котором сейчас показан интерфейс (по `ui_lang_tag` из строк). Если язык телефона приложением не поддерживается,
 * это английский: в таком случае по нему же сортируется и список языков.
 */
@Composable
internal fun uiLocale(): Locale {
    val tag = stringResource(R.string.ui_lang_tag)
    return remember(tag) { Locale.forLanguageTag(tag) }
}

/** Подпись выбранного языка для строки в настройках: «Системный» или название языка на языке интерфейса. */
@Composable
internal fun languageSummary(lang: AppLanguage): String =
    if (lang == AppLanguage.SYSTEM) stringResource(R.string.language_system) else lang.displayName(uiLocale())

/**
 * Окно выбора языка приложения (оформлено так же, как окна быстрых настроек звука в плеере).
 * Сверху закреплён пункт «Системный», под ним (отдельно, с прокруткой) языки по алфавиту.
 */
@Composable
internal fun LanguageDialog(current: AppLanguage, onSelect: (AppLanguage) -> Unit, onDismiss: () -> Unit) {
    val ui = uiLocale()
    val languages = remember(ui) { AppLanguage.sortedFor(ui) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_title)) },
        text = {
            Column {
                LanguageRow(
                    title = stringResource(R.string.language_system),
                    subtitle = stringResource(R.string.language_system_desc),
                    selected = current == AppLanguage.SYSTEM,
                    onClick = { onSelect(AppLanguage.SYSTEM) },
                )
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    languages.forEach { lang ->
                        val name = lang.displayName(ui)
                        val native = lang.nativeName()
                        LanguageRow(
                            title = name,
                            // Название на самом языке: по нему язык находят, даже если интерфейс непонятен
                            subtitle = native.takeUnless { it.equals(name, ignoreCase = true) },
                            selected = current == lang,
                            onClick = { onSelect(lang) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun LanguageRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
