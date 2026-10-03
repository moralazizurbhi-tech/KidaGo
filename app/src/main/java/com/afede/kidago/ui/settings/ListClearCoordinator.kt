package com.afede.kidago.ui.settings

import com.afede.kidago.ui.components.ConfirmationController
import com.afede.kidago.ui.components.ConfirmationRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Idle: nothing pending. ConfirmingClear: the clear awaits the user's answer. */
enum class ClearState { Idle, ConfirmingClear }

/**
 * Owns the manual-clear interaction (FEAT-004 TD, ListClearCoordinator). Triggering only asks; nothing is written
 * until the user confirms (C6, C8). [archiveAndClear] is the store's atomic archive-then-empty, which is a no-op
 * on an empty list (C7, C10). [confirmation] is the shared destructive prompt holder, which enforces one prompt
 * at a time (C9).
 */
class ListClearCoordinator(
    private val confirmation: ConfirmationController,
    private val scope: CoroutineScope,
    private val archiveAndClear: suspend () -> Unit,
) {
    private val _state = MutableStateFlow(ClearState.Idle)
    val state: StateFlow<ClearState> = _state

    /** The clear action was triggered. Ignored while a clear is already pending. */
    fun onClearTapped() {
        if (_state.value != ClearState.Idle) return
        val asked = confirmation.request(
            ConfirmationRequest(
                title = "¿Vaciar la lista actual?",
                confirmLabel = "Vaciar",
                declineLabel = "Cancelar",
                onConfirm = {
                    _state.value = ClearState.Idle
                    scope.launch { archiveAndClear() }
                },
                onDecline = { _state.value = ClearState.Idle },
            ),
        )
        if (asked) _state.value = ClearState.ConfirmingClear
    }
}
