package com.erkantaylan.kitaplik.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erkantaylan.kitaplik.ui.theme.Palette

/**
 * Landing screen: what you are in the middle of, then what you touched last.
 *
 * Both lists stay empty until the reader exists to record a position — an
 * honest empty state rather than invented rows.
 */
@Composable
fun HomeScreen(onBrowse: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.bg)
    ) {
        ScreenTitle("KİTAPLIK")

        SectionLabel("Currently reading")
        Placeholder(
            "Nothing open yet.",
            "Open a book and it will wait for you here, at the page you left."
        )

        SectionLabel("Recently opened")
        Placeholder(
            "No history yet.",
            "Books you have opened appear here, newest first."
        )

        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "Browse the library →",
                color = Palette.accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .testTagged("browse")
                    .clip(RoundedCornerShape(8.dp))
                    .clickableNoRipple(onBrowse)
                    .padding(vertical = 10.dp),
            )
        }
    }
}

@Composable
fun ScreenTitle(text: String) {
    Text(
        text,
        color = Palette.textDim,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = Palette.accent,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun Placeholder(headline: String, detail: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.panel)
            .border(1.dp, Palette.border, RoundedCornerShape(10.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(headline, color = Palette.text, fontSize = 14.sp)
        Text(detail, color = Palette.textDim, fontSize = 12.5.sp, lineHeight = 18.sp)
    }
}

/** A screen that exists in the tab bar but has nothing behind it yet. */
@Composable
fun ComingSoonScreen(title: String, detail: String) {
    Column(Modifier.fillMaxSize().background(Palette.bg)) {
        ScreenTitle(title.uppercase())
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(32.dp),
            ) {
                Text("Not built yet", color = Palette.text, fontSize = 15.sp,
                     fontWeight = FontWeight.SemiBold)
                Text(detail, color = Palette.textDim, fontSize = 13.sp,
                     lineHeight = 19.sp, textAlign = TextAlign.Center)
            }
        }
    }
}
