package com.erkantaylan.kitaplik.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import com.erkantaylan.kitaplik.reader.BionicStrength
import com.erkantaylan.kitaplik.reader.ReaderFont
import com.erkantaylan.kitaplik.reader.ReaderStyle
import com.erkantaylan.kitaplik.reader.TextAlignment
import com.erkantaylan.kitaplik.reader.BookmarkToggle
import com.erkantaylan.kitaplik.reader.bionicSpans
import com.erkantaylan.kitaplik.reader.ReaderViewModel
import com.erkantaylan.kitaplik.reader.formatDuration
import com.erkantaylan.kitaplik.ui.theme.Palette
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Plain reading. Paragraphs in a lazy list, position remembered as you scroll.
 *
 * The list is the only renderer today; bionic and speed reading will sit
 * beside it and share the same position, which is why the view model deals in
 * paragraph indices rather than pixels.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(viewModel: ReaderViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // The figure is only recomputed when the position changes, which means it
    // is calculated the instant you arrive at a new screen — crediting the
    // words you just scrolled past before the time you are about to spend
    // reading them. Ticking here lets the elapsed time catch up while you sit
    // still, which is most of a reading session.
    LaunchedEffect(state.loading) {
        if (state.loading) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(5_000)
            viewModel.onTick()
        }
    }

    // A reading segment ends when the app goes to the background OR when the
    // reader is closed. The view model is keyed on the book and outlives this
    // screen, so leaving composition has to close the segment explicitly —
    // otherwise a session only counts if you background the whole app.
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) viewModel.onPaused()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onPaused()
        }
    }

    // Restore the saved position once the text has been extracted.
    LaunchedEffect(state.startParagraph, state.loading) {
        if (state.loading) return@LaunchedEffect
        if (state.startParagraph > 0 || state.startFraction > 0f) {
            listState.scrollToItem(state.startParagraph)
            // Now that the paragraph is laid out we know its height, so the
            // saved fraction can be turned back into pixels. This is what
            // makes the position survive a font-size change.
            val info = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == state.startParagraph }
            if (info != null && state.startFraction > 0f) {
                listState.scrollBy(state.startFraction * info.size)
            }
        }
    }

    // Record where we are whenever the top visible paragraph changes.
    LaunchedEffect(listState, state.loading) {
        if (state.loading) return@LaunchedEffect
        androidx.compose.runtime.snapshotFlow {
            val index = listState.firstVisibleItemIndex
            val size = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == index }?.size ?: 0
            val fraction = if (size > 0)
                listState.firstVisibleItemScrollOffset.toFloat() / size else 0f
            index to fraction
        }
            .distinctUntilChanged()
            .collect { (index, fraction) -> viewModel.onScrolled(index, fraction) }
    }

    val progress by remember(state.book) {
        derivedStateOf {
            val total = state.book.paragraphs.size
            if (total == 0) 0f
            else (listState.firstVisibleItemIndex.toFloat() / total).coerceIn(0f, 1f)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.bg)
            .semantics { testTagsAsResourceId = true }
    ) {
        ReaderBar(
            title = viewModel.title,
            author = viewModel.author,
            wpm = state.wpm,
            wpmProvisional = state.wpmProvisional,
            minutesLeft = state.minutesLeft,
            bookmarkCount = state.bookmarks.size,
            onResetSpeed = {
                viewModel.resetSpeed()
                Toast.makeText(context, "Reading speed reset for this book",
                               Toast.LENGTH_SHORT).show()
            },
            onBack = onBack,
            onBookmarks = { viewModel.setBookmarksVisible(!state.showBookmarks) },
            onPanel = { viewModel.setPanelVisible(!state.showPanel) },
        )

        if (state.showPanel) {
            ReadingPanel(
                bionic = state.bionic,
                strength = state.bionicStrength,
                style = state.style,
                onBionic = viewModel::setBionic,
                onStrength = viewModel::setBionicStrength,
                onStyle = viewModel::setStyle,
                onReset = viewModel::resetStyle,
            )
        }

        LinearProgressIndicator(
            progress = { progress },
            color = Palette.accent,
            trackColor = Palette.panel,
            drawStopIndicator = {},
            gapSize = 0.dp,
            modifier = Modifier.fillMaxWidth().height(2.dp),
        )

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Palette.accent)
            }

            state.error != null -> Box(
                Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    state.error!!,
                    color = Palette.danger,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag("reader_text"),
                contentPadding = PaddingValues(
                    horizontal = state.style.margin.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.book.paragraphs, key = { it.index }) { paragraph ->
                    val marks = state.bookmarks.filter { it.paragraphIndex == paragraph.index }

                    // Highlight each marked word rather than the whole block.
                    val wordSpacing = state.style.wordSpacing
                    val rendered = androidx.compose.runtime.remember(
                        paragraph.text, marks, state.bionic, state.bionicStrength, wordSpacing,
                    ) {
                        if (marks.isEmpty() && !state.bionic && wordSpacing == 0f) {
                            androidx.compose.ui.text.AnnotatedString(paragraph.text)
                        } else {
                            androidx.compose.ui.text.buildAnnotatedString {
                                append(paragraph.text)
                                // Word weights first, so a bookmark's background
                                // still reads as a mark on top of them.
                                if (state.bionic) {
                                    bionicSpans(paragraph.text, state.bionicStrength)
                                        .forEach { word ->
                                            addStyle(
                                                androidx.compose.ui.text.SpanStyle(
                                                    fontWeight = FontWeight.SemiBold,
                                                ),
                                                word.start, word.headEnd,
                                            )
                                            if (word.headEnd < word.end) addStyle(
                                                androidx.compose.ui.text.SpanStyle(
                                                    color = Palette.readerTail,
                                                ),
                                                word.headEnd, word.end,
                                            )
                                        }
                                }
                                // Compose has no wordSpacing, so each space is
                                // widened by a letterSpacing span. The string is
                                // never rewritten — every position in this app is
                                // a character offset into it.
                                if (wordSpacing > 0f) {
                                    paragraph.text.forEachIndexed { at, ch ->
                                        if (ch == ' ') addStyle(
                                            androidx.compose.ui.text.SpanStyle(
                                                letterSpacing = wordSpacing.em,
                                            ),
                                            at, at + 1,
                                        )
                                    }
                                }
                                marks.forEach { mark ->
                                    val from = (mark.charOffset - paragraph.start)
                                        .coerceIn(0, paragraph.text.length)
                                    val to = (from + maxOf(mark.wordLength, 1))
                                        .coerceIn(from, paragraph.text.length)
                                    addStyle(
                                        androidx.compose.ui.text.SpanStyle(
                                            background = Palette.bookmark,
                                            color = Palette.text,
                                        ),
                                        from, to,
                                    )
                                }
                            }
                        }
                    }

                    var layout by androidx.compose.runtime.remember(paragraph.index) {
                        androidx.compose.runtime.mutableStateOf<
                            androidx.compose.ui.text.TextLayoutResult?>(null)
                    }

                    Text(
                        rendered,
                        color = Palette.readerText,
                        fontSize = state.style.size.sp,
                        lineHeight = (state.style.size * state.style.lineHeight).sp,
                        letterSpacing = state.style.letterSpacing.em,
                        textAlign = when (state.style.align) {
                            TextAlignment.LEFT -> TextAlign.Start
                            TextAlignment.JUSTIFY -> TextAlign.Justify
                        },
                        fontFamily = when (state.style.font) {
                            ReaderFont.SERIF -> FontFamily.Serif
                            ReaderFont.SANS -> FontFamily.SansSerif
                            ReaderFont.MONO -> FontFamily.Monospace
                        },
                        onTextLayout = { layout = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(paragraph.index, layout) {
                                detectTapGestures(
                                    onLongPress = { position ->
                                        // Map the touch to a character, so the
                                        // mark lands on the word under the finger.
                                        val result = layout ?: return@detectTapGestures
                                        val index = result.getOffsetForPosition(position)
                                        val what =
                                            viewModel.toggleBookmarkAt(paragraph.index, index)
                                        Toast.makeText(
                                            context,
                                            when (what) {
                                                BookmarkToggle.ADDED -> "Bookmarked"
                                                BookmarkToggle.REMOVED -> "Bookmark removed"
                                                BookmarkToggle.NO_WORD -> "No word there"
                                            },
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                )
                            }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderBar(
    title: String,
    author: String,
    wpm: Int,
    wpmProvisional: Boolean,
    minutesLeft: Long?,
    bookmarkCount: Int,
    onResetSpeed: () -> Unit,
    onBack: () -> Unit,
    onBookmarks: () -> Unit,
    onPanel: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.panel)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "‹",
            color = Palette.accent,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .testTag("reader_back")
                .clickableNoRipple(onBack)
                .padding(horizontal = 10.dp, vertical = 2.dp),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 6.dp)
                .testTag("reader_stats")
                // Long-press clears this book's measurement, for when a fast
                // scroll through the pages has polluted it.
                .combinedClickable(onClick = {}, onLongClick = onResetSpeed),
        ) {
            Text(title, color = Palette.text, fontSize = 13.sp, maxLines = 1,
                 lineHeight = 15.sp)
            val line = buildList {
                if (author.isNotBlank()) add(author)
                if (wpm > 0) add(if (wpmProvisional) "~$wpm wpm" else "$wpm wpm")
                if (minutesLeft != null) add("${formatDuration(minutesLeft)} left")
            }.joinToString(" · ")
            if (line.isNotBlank()) {
                Text(
                    line,
                    color = if (wpm > 0) Palette.accent else Palette.textDim,
                    fontSize = 10.5.sp,
                    lineHeight = 12.sp,
                    maxLines = 1,
                )
            }
        }
        Text(
            if (bookmarkCount > 0) "\u2691 $bookmarkCount" else "\u2691",
            color = if (bookmarkCount > 0) Palette.accent else Palette.textDim,
            fontSize = 15.sp,
            modifier = Modifier.testTag("bookmarks")
                .clickableNoRipple(onBookmarks)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Text("\u2261", color = Palette.textDim, fontSize = 19.sp,
             modifier = Modifier.testTag("reading_panel")
                 .clickableNoRipple(onPanel).padding(horizontal = 10.dp, vertical = 2.dp))
    }
}



/** Two panes. Spacing belongs under Text — it is a property of the setting. */
private enum class PanelTab(val label: String) { READING("Reading"), TEXT("Text") }

/**
 * The reading panel: how the page is set, tuned while you are looking at it.
 *
 * The pane switcher is a row of tabs rather than a row of buttons, because
 * these choose a view and the chips below them choose a value — two different
 * jobs that should not look alike.
 */
@Composable
private fun ReadingPanel(
    bionic: Boolean,
    strength: BionicStrength,
    style: ReaderStyle,
    onBionic: (Boolean) -> Unit,
    onStrength: (BionicStrength) -> Unit,
    onStyle: (ReaderStyle) -> Unit,
    onReset: () -> Unit,
) {
    var pane by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(PanelTab.READING)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Palette.panel)
            .padding(horizontal = 14.dp)
            .padding(top = 4.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            PanelTab.entries.forEach { tab -> PaneTab(tab, tab == pane) { pane = tab } }
            Box(Modifier.weight(1f))
            Text(
                "Reset",
                color = Palette.textDim,
                fontSize = 11.sp,
                modifier = Modifier
                    .testTag("reset_style")
                    .clickableNoRipple(onReset)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
            )
        }

        when (pane) {
            PanelTab.READING -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Bionic reading", color = Palette.text, fontSize = 12.5.sp)
                        Text("Weight the front of each word",
                             color = Palette.textDim, fontSize = 10.sp)
                    }
                    Segmented("bionic_toggle", listOf("OFF" to false, "ON" to true),
                              bionic) { onBionic(it) }
                }
                // Strength only means anything while bionic is on, so it travels
                // with it rather than sitting live above an off switch.
                if (bionic) Labelled("Strength") {
                    Segmented(
                        "strength",
                        BionicStrength.entries.map { it.label to it },
                        strength, onStrength,
                    )
                }
            }

            PanelTab.TEXT -> {
                Labelled("Font") {
                    Segmented("font", ReaderFont.entries.map { it.label to it },
                              style.font) { onStyle(style.copy(font = it)) }
                }
                Labelled("Align") {
                    Segmented("align", TextAlignment.entries.map { it.label to it },
                              style.align) { onStyle(style.copy(align = it)) }
                }
                Setting("Size", "${style.size.toInt()}", style.size,
                        ReaderStyle.SIZE, "size") { onStyle(style.copy(size = it)) }
                Setting("Line height", String.format("%.2f", style.lineHeight),
                        style.lineHeight, ReaderStyle.LINE_HEIGHT, "line_height") {
                    onStyle(style.copy(lineHeight = it))
                }
                Setting("Letter", String.format("%.2f", style.letterSpacing),
                        style.letterSpacing, ReaderStyle.LETTER_SPACING, "letter_spacing") {
                    onStyle(style.copy(letterSpacing = it))
                }
                Setting("Word", String.format("%.2f", style.wordSpacing),
                        style.wordSpacing, ReaderStyle.WORD_SPACING, "word_spacing") {
                    onStyle(style.copy(wordSpacing = it))
                }
                Setting("Margin", "${style.margin.toInt()}", style.margin,
                        ReaderStyle.MARGIN, "margin") { onStyle(style.copy(margin = it)) }
            }
        }
    }
}

/** A tab: a word with a rule under it when it is the one you are looking at. */
@Composable
private fun PaneTab(tab: PanelTab, active: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .testTag("pane:${tab.name.lowercase()}")
            .clickableNoRipple(onClick)
            .padding(end = 18.dp)
            // Sized to its own label. Without this the rule below fills the
            // whole Row and the first tab shoulders every sibling off-screen.
            .width(IntrinsicSize.Max),
    ) {
        Text(
            tab.label,
            color = if (active) Palette.text else Palette.textDim,
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(top = 6.dp, bottom = 5.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (active) Palette.accent else Palette.panel)
        )
    }
}

@Composable
private fun Labelled(label: String, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Palette.textDim, fontSize = 11.sp,
             modifier = Modifier.width(84.dp))
        control()
    }
}

/**
 * One control, one value: the options joined in a single track rather than
 * scattered as separate pills, so they read as a choice between them.
 */
@Composable
private fun <T> Segmented(
    tag: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Palette.panel2)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (label, value) ->
            val on = value == selected
            Text(
                label,
                color = if (on) Palette.accent else Palette.textDim,
                fontSize = 11.sp,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .testTag("$tag:${label.lowercase()}")
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (on) Palette.bookmark else Palette.panel2)
                    .clickableNoRipple { onSelect(value) }
                    .padding(horizontal = 11.dp, vertical = 5.dp),
            )
        }
    }
}

/** Label, slider and value on one line, so a setting costs one row not two. */
@Composable
private fun Setting(
    label: String,
    value: String,
    current: Float,
    range: ClosedFloatingPointRange<Float>,
    tag: String,
    onChange: (Float) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(26.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Palette.textDim, fontSize = 11.sp,
             modifier = Modifier.width(84.dp))
        Slider(
            value = current,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f).height(18.dp).testTag("slider:$tag"),
            colors = SliderDefaults.colors(
                thumbColor = Palette.accent,
                activeTrackColor = Palette.accent,
                inactiveTrackColor = Palette.panel2,
            ),
        )
        Text(value, color = Palette.text, fontSize = 11.sp,
             fontFamily = FontFamily.Monospace,
             textAlign = TextAlign.End,
             modifier = Modifier.width(44.dp).padding(start = 8.dp))
    }
}
