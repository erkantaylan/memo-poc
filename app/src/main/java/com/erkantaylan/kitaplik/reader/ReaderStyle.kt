package com.erkantaylan.kitaplik.reader

import android.content.Context
import androidx.core.content.edit

/**
 * How the page is set: the typographic choices, separate from what is on it.
 *
 * Ported from the memoriser's reader options, minus two of them. Theme is
 * deliberately absent — one warm theme is the decision, and a picker would
 * undo it. Bold percentage arrived earlier as bionic strength.
 *
 * Word spacing has no equivalent in Compose's TextStyle, so it is applied as a
 * letterSpacing span over each space character. That widens the gaps without
 * touching the string, which matters: every position in this app is a
 * character offset, and rewriting the text to fake spacing would move every
 * bookmark in the book.
 */
data class ReaderStyle(
    val font: ReaderFont = ReaderFont.SERIF,
    /** Body size in sp. */
    val size: Float = 17f,
    /** Multiple of the font size. */
    val lineHeight: Float = 1.65f,
    /** In em, added between characters. */
    val letterSpacing: Float = 0f,
    /** In em, added to each space. */
    val wordSpacing: Float = 0f,
    /** Side margin in dp — the phone's version of column width. */
    val margin: Float = 22f,
    val align: TextAlignment = TextAlignment.LEFT,
) {
    companion object {
        val DEFAULT = ReaderStyle()
        val SIZE = 12f..30f
        val LINE_HEIGHT = 1.2f..2.6f
        val LETTER_SPACING = 0f..0.2f
        val WORD_SPACING = 0f..0.5f
        val MARGIN = 8f..64f
    }
}

enum class ReaderFont(val label: String) { SERIF("Serif"), SANS("Sans"), MONO("Mono") }

/**
 * Ragged right, or flush to both margins.
 *
 * Justified text is easier to track back to on a narrow measure, at the cost
 * of uneven word gaps — which is exactly what the word-spacing control is for
 * when they get bad.
 */
enum class TextAlignment(val label: String) { LEFT("Ragged"), JUSTIFY("Justified") }

/**
 * How you like to read, remembered across books and sessions. These are
 * preferences about your eyes, not about any one book, so they are global.
 *
 * The memoriser kept its blob in sessionStorage, which died with the tab. That
 * was a limitation of where it ran, not a decision worth porting.
 */
class ReaderPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("reader", Context.MODE_PRIVATE)

    var bionic: Boolean
        get() = prefs.getBoolean(KEY_BIONIC, false)
        set(value) = prefs.edit { putBoolean(KEY_BIONIC, value) }

    var strength: BionicStrength
        get() = runCatching {
            BionicStrength.valueOf(prefs.getString(KEY_STRENGTH, null) ?: "")
        }.getOrDefault(BionicStrength.MEDIUM)
        set(value) = prefs.edit { putString(KEY_STRENGTH, value.name) }

    var style: ReaderStyle
        get() = ReaderStyle(
            font = runCatching {
                ReaderFont.valueOf(prefs.getString(KEY_FONT, null) ?: "")
            }.getOrDefault(ReaderFont.SERIF),
            size = prefs.getFloat(KEY_SIZE, ReaderStyle.DEFAULT.size),
            lineHeight = prefs.getFloat(KEY_LINE_HEIGHT, ReaderStyle.DEFAULT.lineHeight),
            letterSpacing = prefs.getFloat(KEY_LETTER, ReaderStyle.DEFAULT.letterSpacing),
            wordSpacing = prefs.getFloat(KEY_WORD, ReaderStyle.DEFAULT.wordSpacing),
            margin = prefs.getFloat(KEY_MARGIN, ReaderStyle.DEFAULT.margin),
            align = runCatching {
                TextAlignment.valueOf(prefs.getString(KEY_ALIGN, null) ?: "")
            }.getOrDefault(TextAlignment.LEFT),
        )
        set(value) = prefs.edit {
            putString(KEY_FONT, value.font.name)
            putFloat(KEY_SIZE, value.size)
            putFloat(KEY_LINE_HEIGHT, value.lineHeight)
            putFloat(KEY_LETTER, value.letterSpacing)
            putFloat(KEY_WORD, value.wordSpacing)
            putFloat(KEY_MARGIN, value.margin)
            putString(KEY_ALIGN, value.align.name)
        }

    private companion object {
        const val KEY_BIONIC = "bionic"
        const val KEY_STRENGTH = "bionic_strength"
        const val KEY_FONT = "font"
        const val KEY_SIZE = "size"
        const val KEY_LINE_HEIGHT = "line_height"
        const val KEY_LETTER = "letter_spacing"
        const val KEY_WORD = "word_spacing"
        const val KEY_MARGIN = "margin"
        const val KEY_ALIGN = "align"
    }
}
