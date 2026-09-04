package com.erkantaylan.kitaplik.text

/**
 * A book reduced to paragraphs, each knowing where it starts in the whole text.
 *
 * The character offset is the single reading position every mode agrees on:
 * paged reading maps it to a paragraph, speed reading will map it to a word
 * index, and none of them need to know about the others.
 */
data class Paragraph(
    val index: Int,
    /** Character offset of this paragraph's first character in the full text. */
    val start: Int,
    val text: String,
)

data class BookText(
    val paragraphs: List<Paragraph>,
    val charCount: Int,
    val wordCount: Int,
) {
    /** The paragraph containing [charOffset], for restoring a saved position. */
    fun paragraphAt(charOffset: Int): Int {
        if (paragraphs.isEmpty()) return 0
        var low = 0
        var high = paragraphs.lastIndex
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (paragraphs[mid].start <= charOffset) low = mid else high = mid - 1
        }
        return low
    }

    fun offsetOf(paragraphIndex: Int): Int =
        paragraphs.getOrNull(paragraphIndex)?.start ?: 0

    companion object {
        val EMPTY = BookText(emptyList(), 0, 0)

        /** Split plain text into paragraphs, recording each one's offset. */
        fun fromPlainText(text: String): BookText {
            val paragraphs = mutableListOf<Paragraph>()
            var offset = 0
            var index = 0
            for (block in text.split(PARAGRAPH_BREAK)) {
                val trimmed = block.trim()
                if (trimmed.isNotEmpty()) {
                    paragraphs += Paragraph(index++, offset, trimmed)
                }
                offset += block.length + 2   // the split consumed a blank line
            }
            val words = WORD.findAll(text).count()
            return BookText(paragraphs, text.length, words)
        }

        private val PARAGRAPH_BREAK = Regex("\\n\\s*\\n")
        private val WORD = Regex("\\p{L}+")
    }
}
