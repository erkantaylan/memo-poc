package com.erkantaylan.kitaplik.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erkantaylan.kitaplik.catalog.Catalog
import com.erkantaylan.kitaplik.catalog.CatalogSource
import com.erkantaylan.kitaplik.catalog.LibraryItem
import com.erkantaylan.kitaplik.download.DownloadState
import com.erkantaylan.kitaplik.download.Downloader
import com.erkantaylan.kitaplik.storage.LibraryStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** One category and the items under it, ready to render. */
data class CatalogSection(val category: String, val items: List<LibraryItem>)

data class CatalogUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val catalog: Catalog? = null,
    val error: String? = null,
    val query: String = "",
    val formatFilter: String? = null,
    val downloads: Map<String, DownloadState> = emptyMap(),
) {
    /** Items matching the current query and format filter, grouped by category. */
    val sections: List<CatalogSection>
        get() {
            val all = catalog?.items ?: return emptyList()
            val needle = query.trim().lowercase()

            return all.asSequence()
                .filter { formatFilter == null || it.format == formatFilter }
                .filter {
                    needle.isEmpty() ||
                        it.title.lowercase().contains(needle) ||
                        it.author.lowercase().contains(needle) ||
                        it.category.lowercase().contains(needle)
                }
                .groupBy { it.category }
                .toSortedMap()
                .map { (category, items) -> CatalogSection(category, items) }
        }

    val shownCount: Int get() = sections.sumOf { it.items.size }

    val totalBytes: Long get() = sections.sumOf { section -> section.items.sumOf { it.bytes } }

    val availableFormats: List<String>
        get() = catalog?.items.orEmpty()
            .map { it.format }
            .distinct()
            .sortedBy { FORMAT_RANK[it] ?: Int.MAX_VALUE }

    fun downloadStateOf(item: LibraryItem): DownloadState =
        downloads[item.id] ?: DownloadState.Absent
}

private val FORMAT_RANK = mapOf("epub" to 0, "md" to 1, "pdf" to 2)

class CatalogViewModel(
    private val source: CatalogSource,
    private val store: LibraryStore,
    private val downloader: Downloader,
) : ViewModel() {

    private val _state = MutableStateFlow(CatalogUiState())
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    /** In-flight downloads, so a second tap can cancel. */
    private val jobs = mutableMapOf<String, Job>()

    init {
        load(initial = true)
    }

    fun onQueryChange(query: String) = _state.update { it.copy(query = query) }

    /** Back with a search or a filter showing clears them before leaving the tab. */
    fun clearFilters() = _state.update { it.copy(query = "", formatFilter = null) }

    /** Tapping the active format clears the filter. */
    fun onFormatFilterToggle(format: String) = _state.update {
        it.copy(formatFilter = if (it.formatFilter == format) null else format)
    }

    fun refresh() = load(initial = false)

    fun fileFor(item: LibraryItem): File = store.fileFor(item)

    fun download(item: LibraryItem) {
        if (jobs.containsKey(item.id)) return
        setDownloadState(item.id, DownloadState.InProgress(0, item.bytes))

        jobs[item.id] = viewModelScope.launch {
            try {
                downloader.download(item) { read, total ->
                    setDownloadState(item.id, DownloadState.InProgress(read, total))
                }
                setDownloadState(item.id, DownloadState.Done)
            } catch (_: CancellationException) {
                setDownloadState(item.id, DownloadState.Absent)
            } catch (t: Throwable) {
                setDownloadState(
                    item.id,
                    DownloadState.Failed(t.message ?: t::class.simpleName ?: "Download failed"),
                )
            } finally {
                jobs.remove(item.id)
            }
        }
    }

    /** Leaves the .part file in place so the next attempt resumes. */
    fun cancel(item: LibraryItem) {
        jobs.remove(item.id)?.cancel()
        setDownloadState(item.id, DownloadState.Absent)
    }

    fun delete(item: LibraryItem) {
        jobs.remove(item.id)?.cancel()
        store.delete(item)
        setDownloadState(item.id, DownloadState.Absent)
    }

    private fun setDownloadState(id: String, downloadState: DownloadState) {
        _state.update { it.copy(downloads = it.downloads + (id to downloadState)) }
    }

    private fun load(initial: Boolean) {
        _state.update { it.copy(loading = initial, refreshing = !initial, error = null) }
        viewModelScope.launch {
            runCatching { source.fetchCatalog() }
                .onSuccess { catalog ->
                    // Anything already on disk shows as downloaded straight away.
                    val onDisk = store.downloadedIds()
                    val restored = catalog.items
                        .filter { it.id in onDisk && store.isDownloaded(it) }
                        .associate { it.id to (DownloadState.Done as DownloadState) }

                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            catalog = catalog,
                            error = null,
                            downloads = restored + it.downloads.filterValues { s ->
                                s is DownloadState.InProgress
                            },
                        )
                    }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            catalog = null,
                            error = throwable.message
                                ?: throwable::class.simpleName
                                ?: "Unknown error",
                        )
                    }
                }
        }
    }
}
