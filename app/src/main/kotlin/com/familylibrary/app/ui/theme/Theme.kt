package com.familylibrary.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = AppPrimary,
    onPrimary = Color.White,
    primaryContainer = AppPrimaryContainer,
    onPrimaryContainer = AppForeground,
    secondary = AppSecondary,
    onSecondary = Color.White,
    tertiary = AppAccent,
    onTertiary = Color.White,
    background = AppBackground,
    onBackground = AppForeground,
    surface = AppSurface,
    onSurface = AppForeground,
    surfaceVariant = AppSurfaceAlt,
    onSurfaceVariant = AppMuted,
    outline = AppOutline,
    outlineVariant = AppOutline,
    error = AppError,
    onError = Color.White,
)

@Composable
fun FamilyLibraryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        content = content,
    )
}
