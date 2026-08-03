package com.familylibrary.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = PrimaryBrown,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7CCC8),
    onPrimaryContainer = OnSurfaceDark,
    secondary = SecondaryGreen,
    onSecondary = Color.White,
    background = BackgroundCream,
    onBackground = OnSurfaceDark,
    surface = SurfaceWhite,
    onSurface = OnSurfaceDark,
    surfaceVariant = Color(0xFFEFEBE9),
    onSurfaceVariant = Color(0xFF5D4037),
    tertiary = AccentOrange,
)

@Composable
fun FamilyLibraryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}
