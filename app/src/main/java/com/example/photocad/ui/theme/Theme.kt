package com.example.photocad.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.photocad.data.AppThemeMode

private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = CardColor,
    primaryContainer = PrimaryLight,
    onPrimaryContainer = Primary,
    surface = Surface,
    onSurface = TextMain,
    background = Surface,
    onBackground = TextMain,
    surfaceVariant = CardColor,
    onSurfaceVariant = TextMuted,
    error = Red,
    onError = CardColor
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF174A91),
    onPrimaryContainer = Color(0xFFD7E3FF),
    surface = Color(0xFF171A20),
    onSurface = Color(0xFFE2E2E9),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF292D35),
    onSurfaceVariant = Color(0xFFC2C6D0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Composable
fun PhotoCADTheme(mode: AppThemeMode, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (mode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val colors = if (dark) DarkColors else LightColors
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(colors.background.toArgb()))
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            WindowInsetsControllerCompat(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            // Compose Scaffold/TopAppBar consume system-bar insets; let them do so once.
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
    }
    MaterialTheme(colorScheme = colors, shapes = AppShapes, typography = AppTypography) {
        Surface(Modifier.fillMaxSize(), color = colors.background, contentColor = colors.onBackground) {
            content()
        }
    }
}
