package com.brandher.webtoondl.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = GreenAccent,
    onPrimary = BgDark,
    secondary = GreenAccentHover,
    onSecondary = BgDark,
    tertiary = DangerRed,
    onTertiary = TextDark,
    background = BgDark,
    onBackground = TextDark,
    surface = SurfaceDark,
    onSurface = TextDark,
    surfaceVariant = SurfaceDark2,
    onSurfaceVariant = SubTextDark,
    outline = BorderDark,
)

private val LightColors = lightColorScheme(
    primary = GreenAccentDark,
    onPrimary = BgLight,
    secondary = GreenAccentHover,
    onSecondary = BgLight,
    background = BgLight,
    onBackground = TextLight,
    surface = SurfaceLight,
    onSurface = TextLight,
    surfaceVariant = SurfaceLight2,
    onSurfaceVariant = SubTextLight,
    outline = BorderLight,
)

@Composable
fun WebtoonDLTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}