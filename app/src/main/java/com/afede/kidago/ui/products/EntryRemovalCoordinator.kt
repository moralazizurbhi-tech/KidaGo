package com.afede.kidago.ui.products

import com.afede.kidago.ui.components.ConfirmationController
import com.afede.kidago.ui.components.ConfirmationRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** List: nothing pending. ConfirmingRemoval: the entry whose removal awaits the user's answer. */
sealed interface RemovalState {
    data object List : RemovalState
    data class ConfirmingRemoval(val code: String) : RemovalState
}

/**
 * Owns the long-press → confirm → remove interaction (FEAT-003 TD, EntryRemovalCoordinator). Nothing is written
 * until the user confirms; the only write is the store's remove (no history is written for a removal, C11).
 * [confirmation] is the shared destructive prompt holder, which also enforces one prompt at a time.
 */
class EntryRemovalCoordinator(
    private val confirmation: ConfirmationController,
    private val scope: CoroutineScope,
    private val remove: suspend (code: String) -> Unit,
) {
    private val _state = MutableStateFlow<RemovalState>(RemovalState.List)
    val state: StateFlow<RemovalState> = _state

    /** A long-press on a row. Ignored while another removal is pending (C7). */
    fun onLongPress(code: String) {
        if (_state.value !is RemovalState.List) return
        val asked = confirmation.request(
            ConfirmationRequest(
                title = "¿Eliminar este producto?",
                confirmLabel = "Eliminar",
                declineLabel = "Cancelar",
                onConfirm = {
                    _state.value = RemovalState.List
                    scope.launch { remove(code) }
                },
                onDecline = { _state.value = RemovalState.List },
            ),
        )
        if (asked) _state.value = RemovalState.ConfirmingRemoval(code)
    }
}
