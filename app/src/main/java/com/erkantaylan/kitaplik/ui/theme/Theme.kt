package com.erkantaylan.kitaplik.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val bg = Color(0xFF14171C)
    val panel = Color(0xFF1B1F26)
    val panel2 = Color(0xFF232831)
    val border = Color(0xFF2C333D)
    val text = Color(0xFFD6DBE2)
    val textDim = Color(0xFF8A93A0)
    val accent = Color(0xFF6DB1FF)
    val danger = Color(0xFFC76B6B)

    val epub = Color(0xFF3D6B4A)
    val md = Color(0xFF4A4A6B)
    val pdf = Color(0xFF6B3D3D)
}

fun formatColor(format: String): Color = when (format) {
    "epub" -> Palette.epub
    "md" -> Palette.md
    "pdf" -> Palette.pdf
    else -> Palette.panel2
}

private val KitaplikColorScheme = darkColorScheme(
    primary = Palette.accent,
    background = Palette.bg,
    surface = Palette.panel,
    surfaceVariant = Palette.panel2,
    onPrimary = Palette.bg,
    onBackground = Palette.text,
    onSurface = Palette.text,
    outline = Palette.border,
    error = Palette.danger,
)

@Composable
fun KitaplikTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // The library is a dark-only surface by design; light mode is not a goal.
    MaterialTheme(colorScheme = KitaplikColorScheme, content = content)
}
