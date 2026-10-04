package com.afede.kidago.ui.scan

import com.afede.kidago.data.AddResult
import com.afede.kidago.data.ListEntry
import com.afede.kidago.data.LookupResult
import com.afede.kidago.data.SyncState
import com.afede.kidago.scanner.ScanResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What Escanear shows (FEAT-002 TD, CaptureCoordinator). [Found.quantity] is the line's total after the scan. */
sealed interface CaptureState {
    data object Idle : CaptureState
    data object Typing : CaptureState
    data object Searching : CaptureState
    data class Found(val code: String, val quantity: Int, val wasExisting: Boolean) : CaptureState
    data class NotFound(val code: String) : CaptureState
    data object Error : CaptureState
}

/**
 * Owns the capture state and one in-flight resolution at a time. A new capture supersedes whatever is resolving or
 * showing, never queues (C9); capture is refused while a catalog sync runs (C10). A scan that matches the catalog is
 * added or accumulated once its lookup answers (C4, C5); "Cantidad" applies only to the latest Found (C11); the state
 * returns to Idle when that Found's entry disappears (C17). Collaborators come in as functions: [lookup] is the
 * CatalogQueryFacade, the rest are the TodaysListEntryStore.
 */
class CaptureCoordinator(
    private val scope: CoroutineScope,
    private val syncState: StateFlow<SyncState>,
    private val lookup: suspend (String) -> LookupResult,
    entries: Flow<List<ListEntry>>,
    private val addOrAccumulate: suspend (String) -> AddResult,
    private val applyQuantity: suspend (code: String, quantityBefore: Int, n: Int) -> Boolean,
    /** Told once per transition into Found, Not Found or Error (the OutcomeFeedbackTrigger); never on a "Cantidad" update. */
    private val onOutcome: (CaptureState) -> Unit = {},
) {
    private val _state = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val state: StateFlow<CaptureState> = _state

    /** The latest Found scan, the only one "Cantidad" can still change. */
    private class Scan(val code: String, val quantityBefore: Int, val wasExisting: Boolean)

    private var latest: Scan? = null
    private var inFlight: Job? = null

    init {
        scope.launch {
            entries.collect { list ->
                val scan = latest ?: return@collect
                if (list.none { it.code == scan.code }) { // removed, or the list was cleared (C17)
                    latest = null
                    _state.value = CaptureState.Idle
                }
            }
        }
    }

    /** The manual field has (or no longer has) text in progress. Never replaces a search or an outcome. */
    fun onTyping(hasText: Boolean) {
        if (hasText && _state.value == CaptureState.Idle) _state.value = CaptureState.Typing
        else if (!hasText && _state.value == CaptureState.Typing) _state.value = CaptureState.Idle
    }

    fun onCapture(capture: ScanResult) {
        if (syncState.value is SyncState.Syncing) return
        inFlight?.cancel()
        latest = null
        if (capture !is ScanResult.Code) { // C8: an unreadable scan is an Error, never a lookup
            outcome(CaptureState.Error)
            return
        }
        _state.value = CaptureState.Searching
        inFlight = scope.launch { resolve(capture.value) }
    }

    private suspend fun resolve(code: String) {
        when (lookup(code)) {
            LookupResult.NotFound -> outcome(CaptureState.NotFound(code))
            LookupResult.Unavailable -> _state.value = CaptureState.Idle // a sync began after capture was accepted
            LookupResult.Found -> {
                // Once the lookup matched, the scan is counted even if a newer capture supersedes it right now.
                val added = withContext(NonCancellable) { addOrAccumulate(code) }
                currentCoroutineContext().ensureActive()
                latest = Scan(code, added.quantityBefore, added.wasExisting)
                outcome(CaptureState.Found(code, added.quantityAfter, added.wasExisting))
            }
        }
    }

    private fun outcome(state: CaptureState) {
        _state.value = state
        onOutcome(state)
    }

    /** "Cantidad" submitted: a whole number 1..99 replaces the +1 of the latest Found scan; anything else is ignored. */
    fun onQuantitySubmit(text: String) {
        val scan = latest ?: return
        val n = validQuantity(text) ?: return
        scope.launch {
            if (applyQuantity(scan.code, scan.quantityBefore, n) && latest === scan) {
                _state.value = CaptureState.Found(scan.code, scan.quantityBefore + n, scan.wasExisting)
            }
        }
    }

    /**
     * "Listo" (C20): from Found, Not Found or Error, back to Idle. On Found, a typed but unsubmitted [pendingQuantity]
     * is applied first exactly as a submit would (C11); no other list write happens.
     */
    fun finishScan(pendingQuantity: String = "") {
        val state = _state.value
        if (state !is CaptureState.Found && state !is CaptureState.NotFound && state != CaptureState.Error) return
        val scan = latest
        val n = validQuantity(pendingQuantity)
        if (state !is CaptureState.Found || scan == null || n == null) {
            latest = null
            _state.value = CaptureState.Idle
            return
        }
        scope.launch {
            applyQuantity(scan.code, scan.quantityBefore, n)
            if (latest === scan) { // a newer capture during the write keeps its own state
                latest = null
                _state.value = CaptureState.Idle
            }
        }
    }

    private fun validQuantity(text: String) =
        text.takeIf { it.isNotEmpty() && it.all { c -> c in '0'..'9' } }?.toIntOrNull()?.takeIf { it in 1..99 }
}
