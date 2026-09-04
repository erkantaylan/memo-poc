package com.erkantaylan.kitaplik.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erkantaylan.kitaplik.catalog.Catalog
import com.erkantaylan.kitaplik.catalog.CatalogSource
import com.erkantaylan.kitaplik.catalog.LibraryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One category and the items under it, ready to render. */
data class CatalogSection(val category: String, val items: List<LibraryItem>)

data class CatalogUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val catalog: Catalog? = null,
    val error: String? = null,
    val query: String = "",
    val formatFilter: String? = null,
) {
    /** Items matching the current query and format filter, grouped by category. */
    val sections: List<CatalogSection>
        get() {
            val all = catalog?.items ?: return emptyList()
            val needle = query.trim().lowercase()

            val matched = all.asSequence()
                .filter { formatFilter == null || it.format == formatFilter }
                .filter {
                    needle.isEmpty() ||
                        it.title.lowercase().contains(needle) ||
                        it.category.lowercase().contains(needle)
                }
                .toList()

            return matched
                .groupBy { it.category }
                .toSortedMap()
                .map { (category, items) -> CatalogSection(category, items) }
        }

    val shownCount: Int get() = sections.sumOf { it.items.size }

    val totalBytes: Long get() = sections.sumOf { section -> section.items.sumOf { it.bytes } }

    /** Formats present in the catalog, in a stable order, for the filter row. */
    val availableFormats: List<String>
        get() = catalog?.items.orEmpty()
            .map { it.format }
            .distinct()
            .sortedBy { FORMAT_RANK[it] ?: Int.MAX_VALUE }
}

private val FORMAT_RANK = mapOf("epub" to 0, "md" to 1, "pdf" to 2)

class CatalogViewModel(private val source: CatalogSource) : ViewModel() {

    private val _state = MutableStateFlow(CatalogUiState())
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    init {
        load(initial = true)
    }

    fun onQueryChange(query: String) = _state.update { it.copy(query = query) }

    /** Tapping the active format clears the filter. */
    fun onFormatFilterToggle(format: String) = _state.update {
        it.copy(formatFilter = if (it.formatFilter == format) null else format)
    }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        _state.update { it.copy(loading = initial, refreshing = !initial, error = null) }
        viewModelScope.launch {
            runCatching { source.fetchCatalog() }
                .onSuccess { catalog ->
                    _state.update {
                        it.copy(loading = false, refreshing = false, catalog = catalog, error = null)
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
