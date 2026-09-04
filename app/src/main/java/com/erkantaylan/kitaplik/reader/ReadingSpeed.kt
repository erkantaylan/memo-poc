package com.erkantaylan.kitaplik.reader

/**
 * How fast you are reading, for this sitting only.
 *
 * Nothing is persisted: the figure describes the session you are in, and
 * closing the book forgets it.
 *
 * The measurement works on segments rather than on individual position
 * updates. Rating each update assumes reading happens while you scroll, and it
 * does not — on a phone you scroll a chunk in half a second and then read it
 * for twenty seconds without touching the screen. Per update, the scroll looks
 * like 2000 wpm and the reading looks like no progress, so nothing is ever
 * counted. A segment covers both and averages them out.
 */
class SpeedTracker(private val charsPerWord: Double) {

    private var committedWords = 0L
    private var committedMillis = 0L

    private var segmentStartOffset = -1
    private var segmentStartAt = 0L
    private var furthestOffset = 0
    private var lastActivityAt = 0L

    /** Feed every position update. */
    fun sample(offset: Int, now: Long = System.currentTimeMillis()) {
        if (segmentStartOffset < 0) {
            begin(offset, now)
            return
        }
        // Still for long enough that the segment is over; bank it and restart.
        if (now - lastActivityAt > IDLE_MS) {
            commit(lastActivityAt)
            begin(offset, now)
            return
        }
        if (offset > furthestOffset) furthestOffset = offset
        lastActivityAt = now
    }

    /** Backgrounded, or the reader was closed. */
    fun stop(now: Long = System.currentTimeMillis()) {
        if (segmentStartOffset < 0) return
        // Credit the time spent on the last screenful, but never beyond the
        // idle cutoff — past that we cannot tell reading from a phone left
        // face up on a table.
        commit(minOf(now, lastActivityAt + IDLE_MS))
        segmentStartOffset = -1
    }

    fun reset() {
        committedWords = 0
        committedMillis = 0
        segmentStartOffset = -1
    }

    /**
     * Words per minute so far, including the segment still in progress so the
     * figure moves while you read. Zero until there is enough to mean anything.
     */
    fun wpm(now: Long = System.currentTimeMillis()): Int {
        var words = committedWords
        var millis = committedMillis

        if (segmentStartOffset >= 0) {
            val openMillis = minOf(now, lastActivityAt + IDLE_MS) - segmentStartAt
            val openWords = ((furthestOffset - segmentStartOffset) / charsPerWord).toLong()
            if (openMillis > 0 && openWords > 0) {
                words += openWords
                millis += openMillis
            }
        }

        if (millis < MIN_VISIBLE_MS || words < MIN_VISIBLE_WORDS) return 0
        val wpm = words * 60_000L / millis
        return if (wpm < MIN_WPM || wpm > MAX_WPM) 0 else wpm.toInt()
    }

    private fun begin(offset: Int, now: Long) {
        segmentStartOffset = offset
        furthestOffset = offset
        segmentStartAt = now
        lastActivityAt = now
    }

    private fun commit(endAt: Long) {
        val millis = endAt - segmentStartAt
        val advanced = furthestOffset - segmentStartOffset
        if (millis < MIN_SEGMENT_MS || advanced <= 0) return

        val words = (advanced / charsPerWord).toLong()
        if (words <= 0) return

        val wpm = words * 60_000.0 / millis
        if (wpm < MIN_WPM || wpm > MAX_WPM) return   // skimming, or asleep

        committedWords += words
        committedMillis += millis
    }

    private companion object {
        const val MIN_SEGMENT_MS = 15_000L
        const val IDLE_MS = 90_000L
        /** Below this the figure is mostly noise, so show nothing. */
        const val MIN_VISIBLE_MS = 25_000L
        const val MIN_VISIBLE_WORDS = 80L
        const val MIN_WPM = 50L
        const val MAX_WPM = 1_200L
    }
}

/** "2 h 40 m", "18 min", "under a minute". */
fun formatDuration(minutes: Long): String = when {
    minutes <= 0 -> "under a minute"
    minutes < 60 -> "$minutes min"
    else -> {
        val h = minutes / 60
        val m = minutes % 60
        if (m == 0L) "$h h" else "$h h $m m"
    }
}
