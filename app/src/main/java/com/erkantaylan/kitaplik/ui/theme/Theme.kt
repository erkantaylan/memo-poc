package com.erkantaylan.kitaplik.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * A full set of colours. Two exist: paper for daylight, night for the dark.
 *
 * Neither is pure. #FFF under dark ink glares; #000 under near-white text
 * halates, the letters bloom and the eye keeps refocusing. Both grounds carry
 * the same warm bias, so the app reads as one thing in either light.
 */
data class Scheme(
    val dark: Boolean,
    val bg: Color,
    val panel: Color,
    val panel2: Color,
    val border: Color,
    val text: Color,
    val textDim: Color,
    val accent: Color,
    val danger: Color,
    val readerText: Color,
    val readerTail: Color,
    /** Behind the chosen option of a segmented control, and behind Save. */
    val selected: Color,
    val epub: Color,
    val md: Color,
    val pdf: Color,
    val epubText: Color,
    val mdText: Color,
    val pdfText: Color,
    /** Behind a highlighted passage, one entry per colour you can pick. */
    val highlights: Map<Highlight, Color>,
    /** Ink over a highlight — one colour serves all of them in a given scheme. */
    val onHighlight: Color,
)

/** What you can paint a bookmark with. */
enum class Highlight(val label: String) {
    YELLOW("Yellow"), GREEN("Green"), BLUE("Blue"), PINK("Pink"), RED("Red")
}

/**
 * Warm near-black under warm off-white: about 11:1 rather than the 21:1 of
 * white on black. Comfortable for a long sitting, far above the 4.5:1 floor.
 */
val Night = Scheme(
    dark = true,
    bg = Color(0xFF1C1A16),
    panel = Color(0xFF24211B),
    panel2 = Color(0xFF2E2A22),
    border = Color(0xFF383126),
    text = Color(0xFFD8CFB8),
    textDim = Color(0xFFA2977E),
    accent = Color(0xFFD99E5A),
    danger = Color(0xFFCE7F6B),
    readerText = Color(0xFFD2C8B0),
    readerTail = Color(0xFFA79E8B),
    selected = Color(0xFF4A3B22),
    epub = Color(0xFF4A6448),
    md = Color(0xFF57506E),
    pdf = Color(0xFF74473C),
    epubText = Color(0xFF9BBE92),
    mdText = Color(0xFFAFA6CE),
    pdfText = Color(0xFFD59B87),
    // Deep enough to leave the ink legible on top: a highlighter's job here is
    // to mark the passage, not to become the brightest thing on the page.
    highlights = mapOf(
        Highlight.YELLOW to Color(0xFF4A3B22),
        Highlight.GREEN to Color(0xFF2C4029),
        Highlight.BLUE to Color(0xFF25384B),
        Highlight.PINK to Color(0xFF4A2A3A),
        Highlight.RED to Color(0xFF4E2A22),
    ),
    onHighlight = Color(0xFFE7DFC9),
)

/**
 * Yellowed paper: a cream ground rather than white, because white at full
 * brightness is the thing that makes a phone tiring to read on. The ink is a
 * warm near-black, not #000, for the same reason in reverse.
 */
val Paper = Scheme(
    dark = false,
    bg = Color(0xFFF7F2E4),
    panel = Color(0xFFEFE8D6),
    panel2 = Color(0xFFE5DCC5),
    border = Color(0xFFD6CCB2),
    text = Color(0xFF2B2620),
    textDim = Color(0xFF6D6353),
    accent = Color(0xFF9C6520),
    danger = Color(0xFF9E3D28),
    readerText = Color(0xFF322C23),
    readerTail = Color(0xFF7A7061),
    // Muted, not a highlighter: a chosen chip should read as chosen, not as
    // the loudest thing on the screen.
    selected = Color(0xFFE4D7B4),
    epub = Color(0xFFCADCC0),
    md = Color(0xFFCFC9E2),
    pdf = Color(0xFFE8C4B4),
    epubText = Color(0xFF3F5C39),
    mdText = Color(0xFF4A4270),
    pdfText = Color(0xFF8A4A33),
    // Actual highlighter colours, kept light enough that dark ink still reads
    // straight through them.
    highlights = mapOf(
        Highlight.YELLOW to Color(0xFFF5E07C),
        Highlight.GREEN to Color(0xFFBFE3A4),
        Highlight.BLUE to Color(0xFFAFD5EE),
        Highlight.PINK to Color(0xFFF3BFD3),
        Highlight.RED to Color(0xFFF2B0A4),
    ),
    onHighlight = Color(0xFF241F19),
)

/**
 * The colours in force.
 *
 * Backed by snapshot state rather than handed down a CompositionLocal: every
 * reference in the app already reads `Palette.something`, so this switches the
 * whole surface without touching a hundred call sites, and reading one inside
 * a composable subscribes it to the change like any other state.
 */
object Palette {
    var scheme by mutableStateOf(Night)

    val dark: Boolean get() = scheme.dark
    val bg: Color get() = scheme.bg
    val panel: Color get() = scheme.panel
    val panel2: Color get() = scheme.panel2
    val border: Color get() = scheme.border
    val text: Color get() = scheme.text
    val textDim: Color get() = scheme.textDim
    val accent: Color get() = scheme.accent
    val danger: Color get() = scheme.danger
    val readerText: Color get() = scheme.readerText
    val readerTail: Color get() = scheme.readerTail
    val epub: Color get() = scheme.epub
    val md: Color get() = scheme.md
    val pdf: Color get() = scheme.pdf
    val epubText: Color get() = scheme.epubText
    val mdText: Color get() = scheme.mdText
    val pdfText: Color get() = scheme.pdfText
    val onHighlight: Color get() = scheme.onHighlight
    val selected: Color get() = scheme.selected

    fun highlight(of: Highlight): Color = scheme.highlights[of] ?: scheme.panel2

    /** The default mark colour, and what the selection bar opens on. */
    val bookmark: Color get() = highlight(Highlight.YELLOW)
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

@Composable
fun KitaplikTheme(content: @Composable () -> Unit) {
    val scheme = Palette.scheme
    val colors = if (scheme.dark) {
        darkColorScheme(
            primary = scheme.accent,
            background = scheme.bg,
            surface = scheme.panel,
            surfaceVariant = scheme.panel2,
            onPrimary = scheme.bg,
            onBackground = scheme.text,
            onSurface = scheme.text,
            outline = scheme.border,
            error = scheme.danger,
        )
    } else {
        lightColorScheme(
            primary = scheme.accent,
            background = scheme.bg,
            surface = scheme.panel,
            surfaceVariant = scheme.panel2,
            onPrimary = scheme.bg,
            onBackground = scheme.text,
            onSurface = scheme.text,
            outline = scheme.border,
            error = scheme.danger,
        )
    }

    // The phone keeps its system bars on, and they draw over the app's ground:
    // on paper their icons have to go dark or they vanish into the cream.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !scheme.dark
                isAppearanceLightNavigationBars = !scheme.dark
            }
        }
    }

    MaterialTheme(colorScheme = colors, content = content)
}
