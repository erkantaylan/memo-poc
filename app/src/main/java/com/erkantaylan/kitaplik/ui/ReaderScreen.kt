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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.erkantaylan.kitaplik.reader.BionicStrength
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
                onBionic = viewModel::setBionic,
                onStrength = viewModel::setBionicStrength,
                onSmaller = { viewModel.adjustFont(-0.1f) },
                onLarger = { viewModel.adjustFont(0.1f) },
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
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.book.paragraphs, key = { it.index }) { paragraph ->
                    val marks = state.bookmarks.filter { it.paragraphIndex == paragraph.index }

                    // Highlight each marked word rather than the whole block.
                    val rendered = androidx.compose.runtime.remember(
                        paragraph.text, marks, state.bionic, state.bionicStrength,
                    ) {
                        if (marks.isEmpty() && !state.bionic) {
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
                        fontSize = (17 * state.fontScale).sp,
                        lineHeight = (28 * state.fontScale).sp,
                        fontFamily = FontFamily.Serif,
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
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "‹",
            color = Palette.accent,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .testTag("reader_back")
                .clickableNoRipple(onBack)
                .padding(horizontal = 10.dp),
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
            Text(title, color = Palette.text, fontSize = 14.sp, maxLines = 1)
            val line = buildList {
                if (author.isNotBlank()) add(author)
                if (wpm > 0) add(if (wpmProvisional) "~$wpm wpm" else "$wpm wpm")
                if (minutesLeft != null) add("${formatDuration(minutesLeft)} left")
            }.joinToString(" · ")
            if (line.isNotBlank()) {
                Text(
                    line,
                    color = if (wpm > 0) Palette.accent else Palette.textDim,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                )
            }
        }
        Text(
            if (bookmarkCount > 0) "\u2691 $bookmarkCount" else "\u2691",
            color = if (bookmarkCount > 0) Palette.accent else Palette.textDim,
            fontSize = 15.sp,
            modifier = Modifier.testTag("bookmarks")
                .clickableNoRipple(onBookmarks).padding(8.dp),
        )
        Text("\u2261", color = Palette.textDim, fontSize = 19.sp,
             modifier = Modifier.testTag("reading_panel")
                 .clickableNoRipple(onPanel).padding(horizontal = 10.dp, vertical = 4.dp))
    }
}



/**
 * The reading panel: how the text is set, tuned while you are looking at it.
 *
 * It sits under the bar rather than in a bottom sheet, so the prose stays on
 * screen — every control here changes the look of the page, and you want to see
 * that happen rather than dismiss a sheet to find out.
 */
@Composable
private fun ReadingPanel(
    bionic: Boolean,
    strength: BionicStrength,
    onBionic: (Boolean) -> Unit,
    onStrength: (BionicStrength) -> Unit,
    onSmaller: () -> Unit,
    onLarger: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Palette.panel)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Bionic reading", color = Palette.text, fontSize = 13.5.sp)
                Text(
                    "Weight the front of each word",
                    color = Palette.textDim,
                    fontSize = 11.sp,
                )
            }
            Text(
                if (bionic) "ON" else "OFF",
                color = if (bionic) Palette.accent else Palette.textDim,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .testTag("bionic_toggle")
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (bionic) Palette.bookmark else Palette.panel2)
                    .clickableNoRipple { onBionic(!bionic) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }

        // Strength only means anything while bionic is on, so it goes with it
        // rather than sitting live above an off switch.
        if (bionic) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Strength", color = Palette.textDim, fontSize = 12.sp,
                     modifier = Modifier.weight(1f))
                BionicStrength.entries.forEach { option ->
                    val on = option == strength
                    Text(
                        option.label,
                        color = if (on) Palette.accent else Palette.textDim,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .testTag("strength:${option.name.lowercase()}")
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (on) Palette.bookmark else Palette.panel2)
                            .clickableNoRipple { onStrength(option) }
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Text size", color = Palette.textDim, fontSize = 12.sp,
                 modifier = Modifier.weight(1f))
            Text("A−", color = Palette.text, fontSize = 15.sp,
                 modifier = Modifier
                     .testTag("font_smaller")
                     .clip(RoundedCornerShape(6.dp))
                     .background(Palette.panel2)
                     .clickableNoRipple(onSmaller)
                     .padding(horizontal = 14.dp, vertical = 5.dp))
            Text("A+", color = Palette.text, fontSize = 15.sp,
                 modifier = Modifier
                     .testTag("font_larger")
                     .clip(RoundedCornerShape(6.dp))
                     .background(Palette.panel2)
                     .clickableNoRipple(onLarger)
                     .padding(horizontal = 14.dp, vertical = 5.dp))
        }
    }
}
