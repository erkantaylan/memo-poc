package com.erkantaylan.kitaplik

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.erkantaylan.kitaplik.ui.AppShell
import com.erkantaylan.kitaplik.ui.CatalogScreen
import com.erkantaylan.kitaplik.ui.CatalogViewModel
import com.erkantaylan.kitaplik.ui.ComingSoonScreen
import com.erkantaylan.kitaplik.ui.ConnectScreen
import com.erkantaylan.kitaplik.ui.HomeScreen
import com.erkantaylan.kitaplik.ui.SettingsScreen
import com.erkantaylan.kitaplik.ui.Tab
import com.erkantaylan.kitaplik.ui.theme.KitaplikTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private enum class Mode { UNSET, DRIVE, HTTP }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val credentials = CredentialStore(this)
        val tokens = TokenProvider(credentials)
        val store = LibraryStore(File(filesDir, "library"))

        setContent {
            KitaplikTheme {
                var mode by remember {
                    mutableStateOf(if (credentials.isConfigured()) Mode.DRIVE else Mode.UNSET)
                }
                var tab by remember { mutableStateOf(Tab.HOME) }

                if (mode == Mode.UNSET) {
                    ConnectScreen(
                        onConnect = { credentials.save(it); mode = Mode.DRIVE },
                        onUseDevServer = { mode = Mode.HTTP },
                    )
                    return@KitaplikTheme
                }

                // Keyed so switching backend rebuilds the graph rather than
                // leaving a view model pointed at the old one.
                key(mode) {
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

                    AppShell(selected = tab, onSelect = { tab = it }) {
                        when (tab) {
                            Tab.HOME -> HomeScreen(onBrowse = { tab = Tab.CLOUD })
                            Tab.CLOUD -> CatalogScreen(vm, title = "CLOUD")
                            Tab.DOWNLOADED -> CatalogScreen(
                                vm, title = "ON DEVICE", onlyDownloaded = true)
                            Tab.SETTINGS -> SettingsScreen(
                                viewModel = vm,
                                sourceLabel = source.name.uppercase(),
                                onDisconnect = { credentials.clear(); mode = Mode.UNSET },
                            )
                        }
                    }
                }
            }
        }
    }
}
