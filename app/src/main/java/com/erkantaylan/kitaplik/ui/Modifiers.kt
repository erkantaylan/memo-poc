package com.erkantaylan.kitaplik.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    clickable(
        interactionSource = androidx.compose.runtime.remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )

fun Modifier.testTagged(tag: String): Modifier = testTag(tag)
