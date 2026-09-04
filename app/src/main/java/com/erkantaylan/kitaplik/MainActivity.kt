package com.erkantaylan.kitaplik

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
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
import com.erkantaylan.kitaplik.catalog.LibraryItem
import com.erkantaylan.kitaplik.download.Downloader
import com.erkantaylan.kitaplik.reader.BookmarkStore
import com.erkantaylan.kitaplik.reader.ReaderViewModel
import com.erkantaylan.kitaplik.reader.ReadingProgressStore
import com.erkantaylan.kitaplik.reader.SpeedStore
import com.erkantaylan.kitaplik.storage.LibraryStore
import com.erkantaylan.kitaplik.ui.AppShell
import com.erkantaylan.kitaplik.ui.CatalogScreen
import com.erkantaylan.kitaplik.ui.CatalogViewModel
import com.erkantaylan.kitaplik.ui.ComingSoonScreen
import com.erkantaylan.kitaplik.ui.ConnectScreen
import com.erkantaylan.kitaplik.ui.HomeScreen
import com.erkantaylan.kitaplik.ui.ReaderScreen
import com.erkantaylan.kitaplik.ui.SettingsScreen
import com.erkantaylan.kitaplik.ui.Tab
import com.erkantaylan.kitaplik.ui.theme.KitaplikTheme
import com.erkantaylan.kitaplik.ui.theme.Palette
import java.io.File

class MainActivity : ComponentActivity() {

    private enum class Mode { UNSET, DRIVE, HTTP }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val credentials = CredentialStore(this)
        val tokens = TokenProvider(credentials)
        val store = LibraryStore(File(filesDir, "library"))
        val progress = ReadingProgressStore(this)
        val bookmarks = BookmarkStore(this)
        val speed = SpeedStore(this)
        val textCache = File(cacheDir, "text")

        setContent {
            // Window insets are handled once, here: every screen below draws
            // inside the safe area, including the reader, which sits outside
            // the tab bar. The phone keeps its system navigation buttons on.
            KitaplikTheme {
              Box(
                Modifier
                    .fillMaxSize()
                    .background(Palette.bg)
                    .safeDrawingPadding()
              ) {
                var mode by remember {
                    mutableStateOf(if (credentials.isConfigured()) Mode.DRIVE else Mode.UNSET)
                }
                var tab by remember { mutableStateOf(Tab.HOME) }
                // Non-null while reading: the reader takes the whole screen,
                // tab bar included, the way a book should.
                // Item plus an optional offset, set when opening a bookmark.
                var reading by remember { mutableStateOf<Pair<LibraryItem, Int?>?>(null) }

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

                    val open: (LibraryItem) -> Unit = { reading = it to null }
                    val current = reading

                    if (current != null) {
                        val (item, offset) = current
                        val readerVm: ReaderViewModel = viewModel(
                            key = "reader-${item.id}-${offset ?: -1}",
                            factory = viewModelFactory {
                                initializer {
                                    ReaderViewModel(item, store, progress, bookmarks,
                                                    speed, textCache, offset)
                                }
                            },
                        )
                        ReaderScreen(readerVm, onBack = { reading = null })
                    } else AppShell(selected = tab, onSelect = { tab = it }) {
                        when (tab) {
                            Tab.HOME -> {
                                // Re-read on every visit so returning from the
                                // reader shows the position just recorded.
                                val catalogItems = vm.state.value.catalog?.items.orEmpty()
                                HomeScreen(
                                    inProgress = progress.inProgress(),
                                    recent = progress.recent(),
                                    bookmarks = bookmarks.recent(),
                                    wpmFor = { speed.effectiveWpm(it) },
                                    onOpen = { id ->
                                        catalogItems.firstOrNull { it.id == id }
                                            ?.let { reading = it to null }
                                    },
                                    onOpenBookmark = { mark ->
                                        catalogItems.firstOrNull { it.id == mark.itemId }
                                            ?.let { reading = it to mark.charOffset }
                                    },
                                    onRemoveBookmark = { bookmarks.remove(it) },
                                    onBrowse = { tab = Tab.CLOUD },
                                )
                            }
                            Tab.CLOUD -> CatalogScreen(vm, title = "CLOUD", onOpen = open)
                            Tab.DOWNLOADED -> CatalogScreen(
                                vm, title = "ON DEVICE", onlyDownloaded = true, onOpen = open)
                            Tab.SETTINGS -> SettingsScreen(
                                viewModel = vm,
                                sourceLabel = source.name.uppercase(),
                                speed = speed,
                                onDisconnect = { credentials.clear(); mode = Mode.UNSET },
                            )
                        }
                    }
                }
              }
            }
        }
    }
}
