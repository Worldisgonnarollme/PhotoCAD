package com.example.photocad.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

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

// Тёмная схема для hero-экрана просмотра чертежа (ui/DrawingScreen.kt).
val ViewerColors = androidx.compose.material3.darkColorScheme(
    primary = Primary,
    onPrimary = CardColor,
    surface = ViewerBackground,
    onSurface = CardColor,
    background = ViewerBackground,
    onBackground = CardColor,
    surfaceVariant = ViewerCard,
    onSurfaceVariant = CardColor
)

@Composable
fun PhotoCADTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content
    )
}
