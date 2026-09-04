package com.erkantaylan.kitaplik.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erkantaylan.kitaplik.ui.theme.Palette

enum class Tab(val label: String, val shape: NavShape, val tag: String) {
    HOME("Home", NavShape.HOME, "tab:home"),
    CLOUD("Cloud", NavShape.CLOUD, "tab:cloud"),
    DOWNLOADED("On device", NavShape.DOWNLOADED, "tab:downloaded"),
    SETTINGS("Settings", NavShape.SETTINGS, "tab:settings"),
}

/**
 * Frame around every screen: content above, tab bar below.
 *
 * The bar carries its own navigation-bar inset rather than the whole app doing
 * so, because the phone keeps the system navigation buttons on — the bar has to
 * sit above them, not behind them.
 */
@Composable
fun AppShell(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.bg)
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .statusBarsPadding()
        ) { content() }

        TabBar(selected, onSelect)
    }
}

@Composable
private fun TabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Palette.panel)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .size(1.dp)
                .background(Palette.border)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Tab.entries.forEach { tab ->
                TabButton(tab, tab == selected) { onSelect(tab) }
            }
        }
    }
}

@Composable
private fun TabButton(tab: Tab, active: Boolean, onClick: () -> Unit) {
    val tint = if (active) Palette.accent else Palette.textDim
    val interaction = remember { MutableInteractionSource() }

    Column(
        Modifier
            .testTag(tab.tag)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        NavIcon(tab.shape, tint, Modifier.size(23.dp))
        Text(
            tab.label,
            color = tint,
            fontSize = 10.5.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            letterSpacing = 0.2.sp,
        )
    }
}
