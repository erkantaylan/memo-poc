package com.erkantaylan.kitaplik.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
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
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Plain reading. Paragraphs in a lazy list, position remembered as you scroll.
 *
 * The list is the only renderer today; bionic and speed reading will sit
 * beside it and share the same position, which is why the view model deals in
 * paragraph indices rather than pixels.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ReaderScreen(viewModel: ReaderViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Restore the saved position once the text has been extracted.
    LaunchedEffect(state.startParagraph, state.loading) {
        if (!state.loading && state.startParagraph > 0) {
            listState.scrollToItem(state.startParagraph)
        }
    }

    // Record where we are whenever the top visible paragraph changes.
    LaunchedEffect(listState, state.loading) {
        if (state.loading) return@LaunchedEffect
        snapshotOfFirstVisible(listState).collect { viewModel.onParagraphVisible(it) }
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
            onBack = onBack,
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
                    Text(
                        paragraph.text,
                        color = Palette.text,
                        fontSize = (17 * state.fontScale).sp,
                        lineHeight = (28 * state.fontScale).sp,
                        fontFamily = FontFamily.Serif,
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
    onBack: () -> Unit,
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
        Text("A−", color = Palette.textDim, fontSize = 15.sp,
             modifier = Modifier.testTag("font_smaller")
                 .clickableNoRipple(onSmaller).padding(8.dp))
        Text("A+", color = Palette.textDim, fontSize = 15.sp,
             modifier = Modifier.testTag("font_larger")
                 .clickableNoRipple(onLarger).padding(8.dp))
    }
}

private fun snapshotOfFirstVisible(state: androidx.compose.foundation.lazy.LazyListState) =
    androidx.compose.runtime.snapshotFlow { state.firstVisibleItemIndex }
        .distinctUntilChanged()
