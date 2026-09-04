package com.erkantaylan.kitaplik.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.erkantaylan.kitaplik.DEV_LIBRARY_URL
import android.widget.Toast
import com.erkantaylan.kitaplik.catalog.ItemKind
import com.erkantaylan.kitaplik.catalog.LibraryItem
import com.erkantaylan.kitaplik.catalog.formatBytes
import com.erkantaylan.kitaplik.download.DownloadState
import com.erkantaylan.kitaplik.open.ExternalOpener
import com.erkantaylan.kitaplik.ui.theme.Palette
import com.erkantaylan.kitaplik.ui.theme.formatColor
import com.erkantaylan.kitaplik.ui.theme.formatTextColor

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CatalogScreen(
    viewModel: CatalogViewModel,
    sourceLabel: String = "",
    onSourceTap: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.bg)
            .semantics { testTagsAsResourceId = true }
    ) {
        Header(
            shown = state.shownCount,
            total = state.catalog?.count,
            totalBytes = state.totalBytes,
            sourceLabel = sourceLabel,
            onSourceTap = onSourceTap,
        )

        SearchField(value = state.query, onValueChange = viewModel::onQueryChange)

        if (state.availableFormats.isNotEmpty()) {
            FormatFilterRow(
                formats = state.availableFormats,
                active = state.formatFilter,
                onToggle = viewModel::onFormatFilterToggle,
            )
        }

        when {
            state.loading -> Centre { CircularProgressIndicator(color = Palette.accent) }

            state.error != null -> Centre { ErrorPanel(state.error!!) }

            state.sections.isEmpty() -> Centre {
                Text("Nothing matches.", color = Palette.text, fontSize = 13.sp)
            }

            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                state.sections.forEach { section ->
                    item(key = "header-${section.category}") {
                        SectionHeader(section.category)
                    }
                    items(section.items, key = { it.id }) { item ->
                        ItemRow(
                            item = item,
                            downloadState = state.downloadStateOf(item),
                            onTap = { onItemTap(context, viewModel, item, state.downloadStateOf(item)) },
                            onLongPress = { viewModel.delete(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(
    shown: Int,
    total: Int?,
    totalBytes: Long,
    sourceLabel: String,
    onSourceTap: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "KİTAPLIK",
                color = Palette.textDim,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            if (sourceLabel.isNotEmpty()) {
                Text(
                    sourceLabel,
                    color = Palette.accent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .testTag("source_badge")
                        .clip(RoundedCornerShape(4.dp))
                        .background(Palette.panel2)
                        .clickable { onSourceTap() }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        if (total != null) {
            val count = if (shown == total) "$total" else "$shown/$total"
            Text(
                "$count · ${formatBytes(totalBytes)}",
                color = Palette.textDim,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Palette.panel)
            .border(1.dp, Palette.border, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        if (value.isEmpty()) {
            Text("Search title or category", color = Palette.textDim, fontSize = 14.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Palette.text, fontSize = 14.sp),
            cursorBrush = SolidColor(Palette.accent),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search"),
        )
    }
}

@Composable
private fun FormatFilterRow(
    formats: List<String>,
    active: String?,
    onToggle: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        formats.forEach { format ->
            val selected = active == format
            val kind = ItemKind.of(format)
            Text(
                kind.label,
                color = if (selected) Palette.text else Palette.textDim,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                modifier = Modifier
                    .testTag("filter:$format")
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) formatColor(format) else Palette.panel)
                    .border(
                        1.dp,
                        if (selected) formatColor(format) else Palette.border,
                        RoundedCornerShape(6.dp),
                    )
                    .clickable { onToggle(format) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun SectionHeader(category: String) {
    Text(
        category.uppercase(),
        color = Palette.accent,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.bg)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/**
 * Tap does whatever the item needs next: download it, cancel an in-flight
 * download, or open a finished one. Long press deletes the local copy.
 */
private fun onItemTap(
    context: android.content.Context,
    viewModel: CatalogViewModel,
    item: LibraryItem,
    downloadState: DownloadState,
) {
    when (downloadState) {
        is DownloadState.InProgress -> viewModel.cancel(item)
        is DownloadState.Absent, is DownloadState.Failed -> viewModel.download(item)
        is DownloadState.Done -> when (item.kind) {
            ItemKind.PDF -> {
                val result = ExternalOpener.open(context, item, viewModel.fileFor(item))
                if (result is ExternalOpener.Result.NoHandler) {
                    Toast.makeText(
                        context,
                        "No app installed that opens ${result.mimeType}",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            // EPUB and markdown get an in-app reader; not built yet.
            else -> Toast.makeText(
                context,
                "${item.kind.label} reader not built yet — long press to delete",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemRow(
    item: LibraryItem,
    downloadState: DownloadState,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp)
            .testTag("item:${item.id}")
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.panel)
            .border(1.dp, Palette.border, RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .padding(12.dp),
    ) {
        // The title gets the full width and is never truncated.
        Text(
            item.title,
            color = Palette.text,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        MetaRow(item, downloadState)

        if (downloadState is DownloadState.InProgress) {
            LinearProgressIndicator(
                progress = { downloadState.fraction },
                color = Palette.accent,
                trackColor = Palette.panel2,
                drawStopIndicator = {},
                gapSize = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }
}

/**
 * Secondary line under the title: format, size, and whatever else earns a
 * place later (downloaded state, progress, date added). Each field is its own
 * composable separated by a dot, so adding one is a single line.
 */
@Composable
private fun MetaRow(item: LibraryItem, downloadState: DownloadState) {
    Row(
        Modifier.padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            item.kind.label,
            color = formatTextColor(item.format),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.4.sp,
        )
        MetaSeparator()
        Text(
            formatBytes(item.bytes),
            color = Palette.textDim,
            fontSize = 11.sp,
        )
        when (downloadState) {
            is DownloadState.Absent -> Unit
            is DownloadState.InProgress -> {
                MetaSeparator()
                Text(
                    "${(downloadState.fraction * 100).toInt()}% · tap to cancel",
                    color = Palette.accent,
                    fontSize = 11.sp,
                )
            }
            is DownloadState.Done -> {
                MetaSeparator()
                Text(
                    if (item.kind == ItemKind.PDF) "on device · tap to open" else "on device",
                    color = Palette.epubText,
                    fontSize = 11.sp,
                )
            }
            is DownloadState.Failed -> {
                MetaSeparator()
                Text(
                    downloadState.message,
                    color = Palette.danger,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun MetaSeparator() {
    Text(
        "·",
        color = Palette.textDim,
        fontSize = 11.sp,
        modifier = Modifier.padding(horizontal = 6.dp),
    )
}

@Composable
private fun ErrorPanel(message: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Cannot reach the library",
            color = Palette.danger,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            message,
            color = Palette.text,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "Serving $DEV_LIBRARY_URL?\n" +
                "python3 -m http.server 8090 in library/\n" +
                "adb reverse tcp:8090 tcp:8090",
            color = Palette.textDim,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun Centre(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}
