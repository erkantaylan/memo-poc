package com.erkantaylan.kitaplik.reader


/**
 * Bionic reading: the front of each word carries more weight, giving the eye a
 * fixation point per word instead of one per line.
 *
 * It is a *rendering*, not a mode. The text, the paragraph split and the
 * character offsets are untouched, so turning it on mid-sentence does not move
 * you, and bookmarks, progress and the speed meter carry on unaware. That is
 * the whole reason it can be a toggle while RSVP cannot.
 */
enum class BionicStrength(val label: String, val fraction: Double) {
    LIGHT("Light", 0.30),
    MEDIUM("Medium", 0.42),
    STRONG("Strong", 0.58);

    /** How many leading characters of a word to weight. Always at least one. */
    fun headOf(length: Int): Int =
        if (length <= 1) length
        else Math.ceil(length * fraction).toInt().coerceIn(1, length - 1)
}

/** One word in a paragraph: where it starts, where its weighted head ends. */
data class WordSpan(val start: Int, val headEnd: Int, val end: Int)

/**
 * Word boundaries for one paragraph, in a single character scan.
 *
 * Apostrophes bind the same way they do for bookmarks, so "don't" is one word
 * and so is "İstanbul'da" — bolding "İst" and leaving "anbul'da" plain is the
 * point, bolding "İst" and then "d" of a separate "da" is not.
 */
fun bionicSpans(text: String, strength: BionicStrength): List<WordSpan> {
    val spans = ArrayList<WordSpan>(text.length / 6 + 4)
    var i = 0
    while (i < text.length) {
        if (!isWordStart(text, i)) { i++; continue }
        var end = i + 1
        while (end < text.length && isWordPart(text, end)) end++
        val length = end - i
        if (length > 0) spans.add(WordSpan(i, i + strength.headOf(length), end))
        i = end
    }
    return spans
}

private fun isWordStart(text: String, i: Int): Boolean = text[i].isLetterOrDigit()

/** An apostrophe belongs to the word only when letters sit on both sides. */
private fun isWordPart(text: String, i: Int): Boolean {
    val c = text[i]
    if (c.isLetterOrDigit()) return true
    if (c != '\'' && c != '’') return false
    return i > 0 && text[i - 1].isLetterOrDigit() &&
        i + 1 < text.length && text[i + 1].isLetterOrDigit()
}
