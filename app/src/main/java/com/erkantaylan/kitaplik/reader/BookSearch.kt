package com.erkantaylan.kitaplik.reader

import com.erkantaylan.kitaplik.text.BookText

/** Where a hit sits: which paragraph, and where inside it. */
data class Match(val paragraph: Int, val start: Int, val end: Int)

/**
 * Find every occurrence of [query] in the book, in reading order.
 *
 * Searching paragraph by paragraph rather than over one joined string keeps
 * every hit already located — a match never straddles a paragraph break, and
 * there is no offset arithmetic to get wrong on the way back out.
 *
 * Matching is case-insensitive through Kotlin's locale-independent folding,
 * which has a useful side effect in Turkish: 'ı' and 'i' both fold to 'I', so
 * typing "istanbul" on a keyboard without the dotless i still finds "ışık".
 */
fun search(book: BookText, query: String, limit: Int = SEARCH_LIMIT): List<Match> {
    val needle = query.trim()
    if (needle.length < MIN_QUERY) return emptyList()

    val hits = ArrayList<Match>()
    for (paragraph in book.paragraphs) {
        var from = 0
        while (true) {
            val at = paragraph.text.indexOf(needle, from, ignoreCase = true)
            if (at < 0) break
            hits += Match(paragraph.index, at, at + needle.length)
            if (hits.size >= limit) return hits
            from = at + needle.length
        }
    }
    return hits
}

/** Two characters is where a search stops matching half the book. */
const val MIN_QUERY = 2

/**
 * Enough hits to navigate by, not so many that a common word costs a scan of
 * the whole book on every keystroke. A search that reaches it is reported as
 * "500+", never as though 500 were the answer.
 */
const val SEARCH_LIMIT = 500
