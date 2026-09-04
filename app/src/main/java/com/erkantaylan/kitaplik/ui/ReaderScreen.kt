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
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
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
import com.erkantaylan.kitaplik.reader.ReaderViewModel
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
    val scope = androidx.compose.runtime.rememberCoroutineScope()

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
            bookmarkCount = state.bookmarks.size,
            onBack = onBack,
            onBookmarks = { viewModel.setBookmarksVisible(!state.showBookmarks) },
            onSmaller = { viewModel.adjustFont(-0.1f) },
            onLarger = { viewModel.adjustFont(0.1f) },
        )

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
                    val marked = paragraph.index in state.markedParagraphs
                    Text(
                        paragraph.text,
                        color = Palette.readerText,
                        fontSize = (17 * state.fontScale).sp,
                        lineHeight = (28 * state.fontScale).sp,
                        fontFamily = FontFamily.Serif,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .then(
                                if (marked) Modifier.background(Palette.panel) else Modifier
                            )
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    val added = viewModel.toggleBookmark(paragraph.index)
                                    Toast.makeText(
                                        context,
                                        if (added) "Bookmarked" else "Bookmark removed",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            )
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
    bookmarkCount: Int,
    onBack: () -> Unit,
    onBookmarks: () -> Unit,
    onSmaller: () -> Unit,
    onLarger: () -> Unit,
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
        Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
            Text(title, color = Palette.text, fontSize = 14.sp, maxLines = 1)
            if (author.isNotBlank()) {
                Text(author, color = Palette.textDim, fontSize = 11.5.sp, maxLines = 1)
            }
        }
        Text(
            if (bookmarkCount > 0) "\u2691 $bookmarkCount" else "\u2691",
            color = if (bookmarkCount > 0) Palette.accent else Palette.textDim,
            fontSize = 15.sp,
            modifier = Modifier.testTag("bookmarks")
                .clickableNoRipple(onBookmarks).padding(8.dp),
        )
        Text("A−", color = Palette.textDim, fontSize = 15.sp,
             modifier = Modifier.testTag("font_smaller")
                 .clickableNoRipple(onSmaller).padding(8.dp))
        Text("A+", color = Palette.textDim, fontSize = 15.sp,
             modifier = Modifier.testTag("font_larger")
                 .clickableNoRipple(onLarger).padding(8.dp))
    }
}


