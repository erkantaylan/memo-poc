package com.erkantaylan.kitaplik

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.erkantaylan.kitaplik.auth.CredentialStore
import com.erkantaylan.kitaplik.auth.TokenProvider
import com.erkantaylan.kitaplik.catalog.CatalogSource
import com.erkantaylan.kitaplik.catalog.DriveCatalogSource
import com.erkantaylan.kitaplik.catalog.HttpCatalogSource
import com.erkantaylan.kitaplik.download.Downloader
import com.erkantaylan.kitaplik.storage.LibraryStore
import com.erkantaylan.kitaplik.ui.CatalogScreen
import com.erkantaylan.kitaplik.ui.CatalogViewModel
import com.erkantaylan.kitaplik.ui.ConnectScreen
import com.erkantaylan.kitaplik.ui.theme.KitaplikTheme
import com.erkantaylan.kitaplik.ui.theme.Palette
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val credentials = CredentialStore(this)
        val tokens = TokenProvider(credentials)
        val store = LibraryStore(File(filesDir, "library"))

        setContent {
            KitaplikTheme {
                // "drive" once credentials exist; "http" only as a dev escape hatch.
                var mode by remember {
                    mutableStateOf(if (credentials.isConfigured()) Mode.DRIVE else Mode.UNSET)
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Palette.bg)
                        .safeDrawingPadding()
                ) {
                    when (mode) {
                        Mode.UNSET -> ConnectScreen(
                            onConnect = { credentials.save(it); mode = Mode.DRIVE },
                            onUseDevServer = { mode = Mode.HTTP },
                        )

                        // Keyed so switching backend rebuilds the whole graph
                        // rather than leaving a view model pointed at the old one.
                        else -> key(mode) {
                            val source: CatalogSource =
                                if (mode == Mode.DRIVE) DriveCatalogSource(tokens)
                                else HttpCatalogSource(DEV_LIBRARY_URL)

                            val downloader = Downloader(source, store)
                            val vm: CatalogViewModel = viewModel(
                                key = "catalog-${mode.name}",
                                factory = viewModelFactory {
                                    initializer { CatalogViewModel(source, store, downloader) }
                                },
                            )
                            CatalogScreen(
                                viewModel = vm,
                                sourceLabel = source.name.uppercase(),
                                onSourceTap = {
                                    credentials.clear()
                                    mode = Mode.UNSET
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    private enum class Mode { UNSET, DRIVE, HTTP }
}
