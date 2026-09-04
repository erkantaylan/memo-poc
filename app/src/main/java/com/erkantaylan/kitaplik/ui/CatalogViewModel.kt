package com.erkantaylan.kitaplik.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erkantaylan.kitaplik.catalog.Book
import com.erkantaylan.kitaplik.catalog.Catalog
import com.erkantaylan.kitaplik.catalog.CatalogSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One category and the books under it, ready to render. */
data class CatalogSection(val category: String, val books: List<Book>)

data class CatalogUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val catalog: Catalog? = null,
    val error: String? = null,
    val query: String = "",
) {
    /** Books matching the current query, grouped by category. */
    val sections: List<CatalogSection>
        get() {
            val books = catalog?.books ?: return emptyList()
            val needle = query.trim().lowercase()
            val matched = if (needle.isEmpty()) books else books.filter {
                it.title.lowercase().contains(needle) ||
                    it.category.lowercase().contains(needle)
            }
            return matched
                .groupBy { it.category }
                .toSortedMap()
                .map { (category, list) -> CatalogSection(category, list) }
        }

    val shownCount: Int get() = sections.sumOf { it.books.size }

    val totalBytes: Long get() = catalog?.books?.sumOf { it.totalBytes } ?: 0L
}

class CatalogViewModel(private val source: CatalogSource) : ViewModel() {

    private val _state = MutableStateFlow(CatalogUiState())
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    init {
        load(initial = true)
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        _state.update {
            it.copy(loading = initial, refreshing = !initial, error = null)
        }
        viewModelScope.launch {
            runCatching { source.fetchCatalog() }
                .onSuccess { catalog ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            catalog = catalog,
                            error = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            catalog = null,
                            error = throwable.message ?: throwable::class.simpleName
                                ?: "Unknown error",
                        )
                    }
                }
        }
    }
}
