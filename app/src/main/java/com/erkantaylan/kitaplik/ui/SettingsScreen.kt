package com.erkantaylan.kitaplik.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.erkantaylan.kitaplik.catalog.formatBytes
import com.erkantaylan.kitaplik.ui.theme.Palette

@Composable
fun SettingsScreen(
    viewModel: CatalogViewModel,
    sourceLabel: String,
    onDisconnect: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val downloaded = state.downloads.count { it.value is
        com.erkantaylan.kitaplik.download.DownloadState.Done }

    Column(Modifier.fillMaxSize().background(Palette.bg)) {
        ScreenTitle("SETTINGS")

        SectionLabel("Library")
        Panel {
            Field("Source", sourceLabel)
            Field("Books in catalog", state.catalog?.count?.toString() ?: "—")
            Field("Downloaded", "$downloaded")
            Field("Catalog built", state.catalog?.generatedAt?.take(10) ?: "—")
            state.catalog?.let {
                Field("Total size", formatBytes(state.totalBytes))
            }
        }

        SectionLabel("Drive")
        Panel {
            Text(
                "Credentials are stored encrypted on this device and only ever " +
                    "reach files this app created — nothing else in your Drive.",
                color = Palette.textDim,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
            )
            Text(
                "Disconnect",
                color = Palette.danger,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(top = 14.dp)
                    .testTagged("disconnect")
                    .clickableNoRipple(onDisconnect),
            )
        }
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.panel)
            .border(1.dp, Palette.border, RoundedCornerShape(10.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) { content() }
}

@Composable
private fun Field(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Palette.textDim, fontSize = 13.sp)
        Text(value, color = Palette.text, fontSize = 13.sp)
    }
}
