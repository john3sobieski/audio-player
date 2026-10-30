package com.example.localplayer

import android.Manifest
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {

    private val vm: PlayerViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* не критично */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PowerSave.load(this)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            AppTheme(vm.themeMode) {
                App(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Каждый вход в приложение (запуск или возврат из фона): проверяем папку на новые и удалённые файлы
        vm.refresh()
    }

    override fun onStop() {
        super.onStop()
        CoverStore.flush()
    }
}

/** Чисто чёрная тёмная схема (как OLED-темы): фон и поверхности #000000, а карточки и диалоги чуть светлее, чтобы читались на чёрном. */
private fun ColorScheme.asOled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF2A2A2A),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF242424),
    // Тональное затемнение (tonalElevation) не должно подкрашивать чёрный в серый
    surfaceTint = Color.Black,
)

@Composable
fun AppTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    // Интенсивное энергосбережение: тема всегда чёрная, выбранная пользователем при этом не теряется
    val dark = if (PowerSave.intensive) true else when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val ctx = LocalContext.current
    val target = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx).asOled() else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme().asOled()
        else -> lightColorScheme()
    }
    // При смене темы цвета плавно перетекают, а не меняются скачком
    val scheme = target.animated()

    // Значки статус-бара и панели навигации и фон окна должны следовать теме приложения, а не системной
    // (иначе при «Ясный» на тёмном телефоне значки были бы белыми на светлом фоне, и наоборот).
    val activity = ctx as? ComponentActivity
    val windowBg = scheme.background.toArgb()
    SideEffect {
        activity?.apply {
            enableEdgeToEdge(
                statusBarStyle = if (dark) SystemBarStyle.dark(Color.Transparent.toArgb())
                else SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb()),
                navigationBarStyle = if (dark) SystemBarStyle.dark(Color.Transparent.toArgb())
                else SystemBarStyle.light(Color.Transparent.toArgb(), 0x80_1B_1B_1B.toInt()),
            )
            window.setBackgroundDrawable(ColorDrawable(windowBg))
        }
    }

    MaterialTheme(colorScheme = scheme, content = content)
}
