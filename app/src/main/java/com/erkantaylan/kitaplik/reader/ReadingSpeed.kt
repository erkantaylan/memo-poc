package com.erkantaylan.kitaplik.reader

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * How fast you actually read, measured rather than assumed.
 *
 * Speed comes from the reading positions the reader already records. The work
 * is not the arithmetic, it is refusing to count things that are not reading:
 * an app left open on a table, a fast scroll to find a passage, a jump
 * backwards to re-read. Each of those would otherwise drag the average
 * somewhere useless.
 */
@Serializable
data class SpeedStats(
    val words: Long = 0,
    val millis: Long = 0,
) {
    val wpm: Int
        get() = if (millis >= 30_000) (words * 60_000L / millis).toInt() else 0

    /** Below this there is not enough evidence to quote a number. */
    val confident: Boolean get() = millis >= 4 * 60_000L && words >= 400

    val minutesRead: Long get() = millis / 60_000L

    operator fun plus(other: SpeedStats) =
        SpeedStats(words + other.words, millis + other.millis)
}

class SpeedStore(context: Context) {

    private val prefs = context.getSharedPreferences("reading_speed", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private fun load(): MutableMap<String, SpeedStats> {
        val raw = prefs.getString(KEY, null) ?: return mutableMapOf()
        return runCatching {
            json.decodeFromString<Map<String, SpeedStats>>(raw).toMutableMap()
        }.getOrDefault(mutableMapOf())
    }

    fun forBook(itemId: String): SpeedStats = load()[itemId] ?: SpeedStats()

    fun overall(): SpeedStats =
        load().values.fold(SpeedStats()) { acc, s -> acc + s }

    fun booksTracked(): Int = load().size

    /**
     * The best number to quote for a book: its own measurement once there is
     * enough of it, otherwise your overall average, otherwise nothing.
     */
    fun effectiveWpm(itemId: String): Int {
        val book = forBook(itemId)
        if (book.confident) return book.wpm
        val all = overall()
        return if (all.confident) all.wpm else 0
    }

    fun record(itemId: String, words: Long, millis: Long) {
        if (words <= 0 || millis <= 0) return
        val map = load()
        map[itemId] = (map[itemId] ?: SpeedStats()) + SpeedStats(words, millis)
        write(map)
    }

    fun resetBook(itemId: String) = write(load().apply { remove(itemId) })

    fun resetAll() = prefs.edit { remove(KEY) }

    private fun write(map: Map<String, SpeedStats>) {
        prefs.edit { putString(KEY, json.encodeToString(map)) }
    }

    private companion object {
        const val KEY = "speed_v1"
    }
}

/**
 * Turns a stream of reading positions into measured reading time.
 *
 * The naive approach — rate each gap between two position updates — does not
 * survive contact with how people read on a phone. You scroll a chunk in half
 * a second, then read it for twenty seconds without touching the screen. Rated
 * per gap, the scroll looks like 2000 wpm and gets thrown out as scrubbing,
 * while the twenty seconds of actual reading looks like zero progress and gets
 * thrown out too. Nothing is ever counted.
 *
 * So this measures a *segment* instead: from when you started reading to when
 * you stopped, against how far you got. Bursts of scrolling and the still
 * stretches between them average out inside the segment, which is exactly the
 * behaviour we want. The gates then apply to the segment as a whole.
 *
 * A segment ends when you put the book down (no movement for [IDLE_MS]), leave
 * the app, or close the reader.
 */
class SpeedTracker(private val charsPerWord: Double) {

    private var segmentStartOffset = -1
    private var segmentStartAt = 0L
    private var furthestOffset = 0
    private var lastActivityAt = 0L

    /**
     * Feed every position update. Returns words and milliseconds to credit
     * when a segment closes, otherwise null.
     */
    fun sample(offset: Int, now: Long = System.currentTimeMillis()): Pair<Long, Long>? {
        if (segmentStartOffset < 0) {
            begin(offset, now)
            return null
        }

        // Idle for long enough that the previous segment is over. Close it and
        // start a fresh one here.
        if (now - lastActivityAt > IDLE_MS) {
            val closed = close(lastActivityAt)
            begin(offset, now)
            return closed
        }

        if (offset > furthestOffset) furthestOffset = offset
        lastActivityAt = now
        return null
    }

    /** Closes the segment: leaving the reader, or backgrounding the app. */
    fun stop(now: Long = System.currentTimeMillis()): Pair<Long, Long>? {
        if (segmentStartOffset < 0) return null
        // Credit the time spent on the last screenful, but never more than the
        // idle cutoff — beyond that we cannot tell reading from a phone left
        // face up on a table.
        val until = minOf(now, lastActivityAt + IDLE_MS)
        val closed = close(until)
        segmentStartOffset = -1
        return closed
    }

    private fun begin(offset: Int, now: Long) {
        segmentStartOffset = offset
        furthestOffset = offset
        segmentStartAt = now
        lastActivityAt = now
    }

    private fun close(endAt: Long): Pair<Long, Long>? {
        val millis = endAt - segmentStartAt
        val advanced = furthestOffset - segmentStartOffset
        if (millis < MIN_SEGMENT_MS || advanced <= 0) return null

        val words = (advanced / charsPerWord).toLong()
        if (words <= 0) return null

        val wpm = words * 60_000.0 / millis
        if (wpm < MIN_WPM || wpm > MAX_WPM) return null   // skimming, or asleep

        return words to millis
    }

    private companion object {
        /** Shorter than this and the reading rate is mostly noise. */
        const val MIN_SEGMENT_MS = 20_000L
        const val IDLE_MS = 90_000L
        const val MIN_WPM = 50.0
        const val MAX_WPM = 1_200.0
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
