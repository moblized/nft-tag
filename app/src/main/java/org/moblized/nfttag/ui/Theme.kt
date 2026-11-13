package org.moblized.nfttag.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.moblized.nfttag.ThemeMode

private val Accent = Color(0xFF8AA0FF)
private val AccentLight = Color(0xFF4F6DF5)

private val LightColors = lightColorScheme(
    primary = AccentLight,
    background = Color(0xFFF7F8FB),
    surface = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Accent,
    background = Color(0xFF14161C),
    surface = Color(0xFF1C1F27),
)

private val BlackColors = darkColorScheme(
    primary = Accent,
    background = Color(0xFF000000),
    surface = Color(0xFF0A0A0A),
)

@Composable
fun NftTagTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when (mode) {
        ThemeMode.SYSTEM -> if (dark) DarkColors else LightColors
        ThemeMode.LIGHT -> LightColors
        ThemeMode.DARK -> DarkColors
        ThemeMode.BLACK -> BlackColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
