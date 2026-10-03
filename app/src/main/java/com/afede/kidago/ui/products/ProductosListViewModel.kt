package com.afede.kidago.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afede.kidago.data.ListEntry
import com.afede.kidago.ui.components.ConfirmationController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** What Productos shows: the entries newest-added first, and the total of all quantities for the header. */
data class ProductosState(val entries: List<ListEntry>, val total: Int) {
    val isEmpty get() = entries.isEmpty()
}

/** The store keeps insertion order (oldest first); Productos shows the most recently added first (C8). */
internal fun productosState(entries: List<ListEntry>, total: Int) = ProductosState(entries.asReversed(), total)

/**
 * The Productos screen's state holder (FEAT-003 TD). The list is derived from the store's live flows, so it cannot
 * diverge from them and has no quantity-editing operation (C3); removal goes through [removal], after confirmation.
 */
class ProductosListViewModel(
    entries: Flow<List<ListEntry>>,
    total: Flow<Int>,
    remove: suspend (code: String) -> Unit,
) : ViewModel() {
    /** Null until the store's first emission, so the empty state never flashes before the list has loaded. */
    val state: StateFlow<ProductosState?> =
        combine(entries, total, ::productosState).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val confirmation = ConfirmationController()
    val removal = EntryRemovalCoordinator(confirmation, viewModelScope, remove)
}
