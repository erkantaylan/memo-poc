package com.erkantaylan.kitaplik.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    /*
     * One reading theme, warm and low-glare.
     *
     * Not pure black: a #000 ground under near-white text produces halation —
     * the text blooms and the eye keeps refocusing. The ground here is a warm
     * near-black and the ink a warm off-white, which lands around 11:1 rather
     * than the 21:1 of white-on-black. Comfortable for a long sitting, still
     * far above the 4.5:1 accessibility floor.
     *
     * The hue bias is inherited from the reading app this replaces, whose
     * default theme was sepia — it was the right call and it is kept.
     */
    val bg = Color(0xFF1C1A16)        // warm near-black, the page
    val panel = Color(0xFF24211B)     // cards, bars
    val panel2 = Color(0xFF2E2A22)    // insets inside a card
    val border = Color(0xFF383126)
    val text = Color(0xFFD8CFB8)      // warm ink, not white
    val textDim = Color(0xFFA2977E)
    val accent = Color(0xFFD99E5A)    // amber, from the same family as the ink
    val danger = Color(0xFFCE7F6B)

    /** Body text sits a touch below UI text — long passages read softer. */
    val readerText = Color(0xFFD2C8B0)

    /** Behind a bookmarked word: present, but not shouting. */
    val bookmark = Color(0xFF4A3B22)

    // Chip / badge fills, desaturated so they sit inside the warm ground.
    val epub = Color(0xFF4A6448)
    val md = Color(0xFF57506E)
    val pdf = Color(0xFF74473C)

    // Lighter variants, for a format drawn as text rather than a filled chip.
    val epubText = Color(0xFF9BBE92)
    val mdText = Color(0xFFAFA6CE)
    val pdfText = Color(0xFFD59B87)
}

/** Fill colour, for chips and badges. */
fun formatColor(format: String): Color = when (format) {
    "epub" -> Palette.epub
    "md" -> Palette.md
    "pdf" -> Palette.pdf
    else -> Palette.panel2
}

/** Foreground colour, for the format drawn as text against a panel. */
fun formatTextColor(format: String): Color = when (format) {
    "epub" -> Palette.epubText
    "md" -> Palette.mdText
    "pdf" -> Palette.pdfText
    else -> Palette.textDim
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
