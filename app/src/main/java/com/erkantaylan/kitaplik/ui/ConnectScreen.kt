package com.erkantaylan.kitaplik.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erkantaylan.kitaplik.auth.DriveCredentials
import com.erkantaylan.kitaplik.ui.theme.Palette

/**
 * One-time Drive connection. The credential blob is produced on the desktop by
 * `tools/make_app_credentials.py` and pasted here once; it is then stored
 * encrypted and never asked for again. There is no login screen because there
 * is no account to log into — the app carries the owner's own credential.
 */
@Composable
fun ConnectScreen(
    onConnect: (DriveCredentials) -> Unit,
    onUseDevServer: () -> Unit,
) {
    var blob by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.bg)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Connect Drive",
            color = Palette.text,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Paste the credential blob from the desktop:\n" +
                "python3 tools/make_app_credentials.py",
            color = Palette.textDim,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 12.dp),
        )

        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
                .heightIn(min = 120.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Palette.panel)
                .border(1.dp, Palette.border, RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            if (blob.isEmpty()) {
                Text("Paste here…", color = Palette.textDim, fontSize = 13.sp)
            }
            BasicTextField(
                value = blob,
                onValueChange = { blob = it; error = null },
                textStyle = TextStyle(
                    color = Palette.text,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                ),
                cursorBrush = SolidColor(Palette.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("credential_input"),
            )
        }

        error?.let {
            Text(it, color = Palette.danger, fontSize = 13.sp,
                 modifier = Modifier.padding(top = 12.dp))
        }

        Text(
            "Connect",
            color = Palette.bg,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(top = 20.dp)
                .fillMaxWidth()
                .testTag("connect_button")
                .clip(RoundedCornerShape(10.dp))
                .background(Palette.accent)
                .clickable {
                    runCatching { DriveCredentials.parse(blob) }
                        .onSuccess(onConnect)
                        .onFailure { error = it.message ?: "Could not read those credentials" }
                }
                .padding(vertical = 14.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        Text(
            "Use the dev server instead",
            color = Palette.textDim,
            fontSize = 13.sp,
            modifier = Modifier
                .padding(top = 18.dp)
                .fillMaxWidth()
                .testTag("use_dev_server")
                .clickable { onUseDevServer() }
                .padding(vertical = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
