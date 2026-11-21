package com.afede.kidago.ui.price

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afede.kidago.data.LookupResult
import com.afede.kidago.data.SyncState
import com.afede.kidago.scanner.BarcodeScanner
import com.afede.kidago.scanner.ScanResult
import com.afede.kidago.ui.scan.BarcodeScannerInput
import com.afede.kidago.ui.scan.ManualEntryInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** What Consultar precio shows (FEAT-006). Nothing here ever touches the list. */
sealed interface PriceState {
    data object Idle : PriceState
    data object Searching : PriceState
    data class Found(val code: String, val priceCents: Long?) : PriceState
    data class NotFound(val code: String) : PriceState
    data object Error : PriceState
}

/**
 * The Precio tab (FEAT-006): looks a code up and shows its price, never adding it (C1). A new capture replaces a shown
 * price (C3), but Not Found and Error stay until "Volver" (C9); captures count only while the tab is shown (C4) and never
 * during a sync; leaving the tab, or "Volver", returns it to its base (C3, C5). Manual entry works like Escanear's (C8).
 */
class PriceLookupViewModel(
    scanner: BarcodeScanner,
    private val syncState: StateFlow<SyncState>,
    private val lookup: suspend (String) -> LookupResult,
    /** Told once per result, for the sound and vibration cues. */
    private val onOutcome: (PriceState) -> Unit = {},
) : ViewModel() {
    private val _state = MutableStateFlow<PriceState>(PriceState.Idle)
    val state: StateFlow<PriceState> = _state

    private val manual = ManualEntryInput(::onCapture)
    val typed: StateFlow<String> = manual.value

    private var active = false
    private var inFlight: Job? = null

    @Suppress("unused") // collects from the scanner for as long as this ViewModel lives
    private val hardware = BarcodeScannerInput(scanner, viewModelScope, ::onCapture)

    /** The tab is shown and resumed (or not). Leaving it clears the result, so coming back never shows a stale one (C5). */
    fun setScreenActive(active: Boolean) {
        this.active = active
        if (!active) reset()
    }

    fun onTyped(text: String) {
        if (!blocked()) manual.onChange(text)
    }

    /** Not Found and Error take no new capture until "Volver" (C9). */
    private fun blocked() = _state.value is PriceState.NotFound || _state.value == PriceState.Error

    fun onSubmit() { manual.submit() }

    /** "Volver": back to the base of the tab (C3). */
    fun onBack() = reset()

    private fun reset() {
        inFlight?.cancel()
        manual.clear()
        _state.value = PriceState.Idle
    }

    private fun onCapture(capture: ScanResult) {
        if (!active || syncState.value is SyncState.Syncing || blocked()) return
        inFlight?.cancel()
        if (capture !is ScanResult.Code) { outcome(PriceState.Error); return }
        _state.value = PriceState.Searching
        inFlight = viewModelScope.launch {
            when (val result = lookup(capture.value)) {
                is LookupResult.Found -> outcome(PriceState.Found(capture.value, result.priceCents))
                LookupResult.NotFound -> outcome(PriceState.NotFound(capture.value))
                LookupResult.Unavailable -> _state.value = PriceState.Idle // a sync began after the capture was accepted
            }
        }
    }

    private fun outcome(state: PriceState) {
        _state.value = state
        manual.clear()
        onOutcome(state)
    }
}
