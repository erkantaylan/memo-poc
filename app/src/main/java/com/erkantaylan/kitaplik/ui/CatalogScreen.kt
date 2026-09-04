package com.erkantaylan.kitaplik.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.erkantaylan.kitaplik.DEV_LIBRARY_URL
import com.erkantaylan.kitaplik.catalog.ItemKind
import com.erkantaylan.kitaplik.catalog.LibraryItem
import com.erkantaylan.kitaplik.catalog.formatBytes
import com.erkantaylan.kitaplik.ui.theme.Palette
import com.erkantaylan.kitaplik.ui.theme.formatColor

@Composable
fun CatalogScreen(viewModel: CatalogViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.bg)
    ) {
        Header(
            shown = state.shownCount,
            total = state.catalog?.count,
            totalBytes = state.totalBytes,
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
                        ItemRow(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(shown: Int, total: Int?, totalBytes: Long) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "KİTAPLIK",
            color = Palette.textDim,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
        )
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
            modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun ItemRow(item: LibraryItem) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.panel)
            .border(1.dp, Palette.border, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Titles repeat across formats, so the format leads the row.
        FormatBadge(item)
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                item.title,
                color = Palette.text,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = 3,
            )
            Text(
                formatBytes(item.bytes),
                color = Palette.textDim,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun FormatBadge(item: LibraryItem) {
    Box(
        Modifier
            .width(52.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(formatColor(item.format))
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            item.kind.label,
            color = Palette.text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
        )
    }
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
