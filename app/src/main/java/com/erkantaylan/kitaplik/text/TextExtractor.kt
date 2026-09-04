package com.erkantaylan.kitaplik.text

import android.util.Xml
import androidx.core.text.HtmlCompat
import com.erkantaylan.kitaplik.catalog.ItemKind
import com.erkantaylan.kitaplik.catalog.LibraryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/**
 * Turns a downloaded file into readable prose.
 *
 * Extraction is cached beside the source: unzipping and de-HTML-ing a 5 MB
 * EPUB takes long enough to notice, and a book is opened far more often than
 * it changes.
 */
object TextExtractor {

    suspend fun extract(item: LibraryItem, file: File, cacheDir: File): BookText =
        withContext(Dispatchers.IO) {
            val cache = File(cacheDir, "${item.id}.txt")
            if (cache.isFile && cache.lastModified() >= file.lastModified()) {
                return@withContext BookText.fromPlainText(cache.readText())
            }

            val plain = when (item.kind) {
                ItemKind.EPUB -> fromEpub(file)
                ItemKind.MARKDOWN -> fromMarkdown(file.readText())
                else -> throw IOException("${item.kind.label} cannot be read in the app")
            }

            cacheDir.mkdirs()
            runCatching { cache.writeText(plain) }
            BookText.fromPlainText(plain)
        }

    fun clearCache(cacheDir: File, item: LibraryItem) {
        File(cacheDir, "${item.id}.txt").delete()
    }

    // ------------------------------------------------------------------ epub

    private fun fromEpub(file: File): String {
        ZipFile(file).use { zip ->
            val container = zip.getEntry("META-INF/container.xml")
                ?: throw IOException("Not a valid EPUB: no container.xml")
            val opfPath = Regex("""full-path=["']([^"']+)["']""")
                .find(zip.getInputStream(container).reader().readText())
                ?.groupValues?.get(1)
                ?: throw IOException("Not a valid EPUB: cannot locate the OPF")

            val opfEntry = zip.getEntry(opfPath) ?: throw IOException("OPF missing: $opfPath")
            val spine = zip.getInputStream(opfEntry).use { readSpine(it) }
            if (spine.isEmpty()) throw IOException("EPUB spine is empty")

            val base = opfPath.substringBeforeLast('/', "")
            val chapters = StringBuilder()
            for (href in spine) {
                val path = resolve(base, href.substringBefore('#'))
                val entry = zip.getEntry(path) ?: continue
                val html = zip.getInputStream(entry).reader().readText()
                val text = htmlToText(html)
                if (text.isNotBlank()) {
                    chapters.append(text).append("\n\n")
                }
            }
            return tidy(chapters.toString())
        }
    }

    /** Manifest id -> href, then spine order. A real parser, not regex. */
    private fun readSpine(input: java.io.InputStream): List<String> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        val manifest = mutableMapOf<String, String>()
        val order = mutableListOf<String>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "item" -> {
                        val id = parser.getAttributeValue(null, "id")
                        val href = parser.getAttributeValue(null, "href")
                        if (id != null && href != null) manifest[id] = href
                    }
                    "itemref" -> parser.getAttributeValue(null, "idref")?.let { order += it }
                }
            }
            event = parser.next()
        }
        return order.mapNotNull { manifest[it] }
    }

    private fun resolve(base: String, relative: String): String {
        if (base.isEmpty()) return relative
        val parts = ArrayDeque<String>()
        for (segment in "$base/$relative".split('/')) {
            when (segment) {
                "", "." -> {}
                ".." -> parts.removeLastOrNull()
                else -> parts.addLast(segment)
            }
        }
        return parts.joinToString("/")
    }

    // HtmlCompat normalises whitespace AND drops control characters, so a
    // paragraph marker has to be ordinary printable text to survive it.
    private const val PARA = "@@KITAPLIK-PARAGRAPH@@"

    private fun htmlToText(html: String): String {
        val prepared = html
            // <head> carries <title>, which HtmlCompat would otherwise render
            // as body text — that is the book title repeated on every chapter.
            .replace(Regex("(?is)<head[^>]*>.*?</head>"), "")
            .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), "")
            .replace(Regex("(?i)</(p|div|h[1-6]|li|blockquote|tr|section|article)>"), PARA)
            .replace(Regex("(?i)<br\\s*/?>"), PARA)

        return HtmlCompat.fromHtml(prepared, HtmlCompat.FROM_HTML_MODE_COMPACT)
            .toString()
            .split(PARA)
            .joinToString("\n\n") { it.trim() }
    }

    // -------------------------------------------------------------- markdown

    private val FRONT_MATTER = Regex("""\A---\r?\n.*?\r?\n---\r?\n""", RegexOption.DOT_MATCHES_ALL)

    fun fromMarkdown(raw: String): String {
        var text = FRONT_MATTER.replace(raw, "")          // metadata, not prose
        text = text
            .replace(Regex("(?s)```.*?```"), "")
            .replace(Regex("`([^`]+)`"), "$1")
            .replace(Regex("""!\[([^\]]*)]\([^)]*\)"""), "$1")
            .replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1")
            .replace(Regex("(?m)^#{1,6}\\s+"), "")
            .replace(Regex("""(\*\*|__)(.+?)\1"""), "$2")
            .replace(Regex("""(\*|_)(.+?)\1"""), "$2")
            .replace(Regex("~~(.+?)~~"), "$1")
            .replace(Regex("(?m)^[-*_]{3,}$"), "")
            .replace(Regex("(?m)^>\\s?"), "")
            .replace(Regex("(?m)^[ \\t]*[-*+]\\s+"), "")
            .replace(Regex("(?m)^[ \\t]*\\d+\\.\\s+"), "")
        return tidy(text)
    }

    private fun tidy(text: String): String = text
        .replace("\r\n", "\n")
        // HtmlCompat substitutes U+FFFC for every <img>; a reader wants none.
        .replace("\uFFFC", "")
        .replace("\uFEFF", "")
        .replace(' ', ' ')
        .replace(Regex("(?m)[ \\t]+$"), "")
        .replace(Regex("(?m)^[ \\t]+"), "")
        .replace(Regex("[ \\t]{2,}"), " ")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}
