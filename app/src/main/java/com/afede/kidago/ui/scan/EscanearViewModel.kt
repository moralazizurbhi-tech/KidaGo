package com.afede.kidago.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afede.kidago.data.AddResult
import com.afede.kidago.data.ListEntry
import com.afede.kidago.data.LookupResult
import com.afede.kidago.data.SyncState
import com.afede.kidago.scanner.BarcodeScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Wires the two capture inputs to the [CaptureCoordinator] and exposes what Escanear shows (FEAT-002 TD). */
class EscanearViewModel(
    scanner: BarcodeScanner,
    syncState: StateFlow<SyncState>,
    lookup: suspend (String) -> LookupResult,
    entries: Flow<List<ListEntry>>,
    addOrAccumulate: suspend (String) -> AddResult,
    applyQuantity: suspend (code: String, quantityBefore: Int, n: Int) -> Boolean,
    onOutcome: (CaptureState) -> Unit = {},
) : ViewModel() {
    private val coordinator = CaptureCoordinator(viewModelScope, syncState, lookup, entries, addOrAccumulate, applyQuantity, onOutcome)
    private val manual = ManualEntryInput(coordinator::onCapture)

    @Suppress("unused") // collects from the scanner for as long as this ViewModel lives
    private val hardware = BarcodeScannerInput(scanner, viewModelScope, coordinator::onCapture)

    val state: StateFlow<CaptureState> = coordinator.state
    val typed: StateFlow<String> = manual.value

    init {
        // A code that was added is done with: free the field for the next one. After Not Found / Error it stays, to be checked.
        viewModelScope.launch { coordinator.state.collect { if (it is CaptureState.Found) manual.clear() } }
    }

    fun onTyped(text: String) {
        manual.onChange(text)
        coordinator.onTyping(manual.value.value.isNotEmpty())
    }

    fun onSubmit() { manual.submit() }

    fun onQuantitySubmit(text: String) = coordinator.onQuantitySubmit(text)

    fun onQuantityEdit(text: String) = coordinator.onQuantityEdit(text)

    /** "Listo": apply any pending "Cantidad", return to Idle and free the field (C20). */
    fun onFinish(pendingQuantity: String) {
        coordinator.finishScan(pendingQuantity)
        manual.clear()
    }
}
