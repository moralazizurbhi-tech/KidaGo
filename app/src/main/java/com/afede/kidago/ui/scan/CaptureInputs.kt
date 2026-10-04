package com.afede.kidago.ui.scan

import com.afede.kidago.scanner.BarcodeScanner
import com.afede.kidago.scanner.ScanResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Hardware capture (FEAT-002 TD, BarcodeScannerInput). Hands every capture attempt to [onCapture] as it arrives: no
 * submit step (C1), the code exactly as delivered (C3). A failed read, or a code with nothing in it, is a [ScanResult.Failure]
 * so it can never be looked up as a code (C8). Gating while capture is disabled belongs to the coordinator.
 */
class BarcodeScannerInput(scanner: BarcodeScanner, scope: CoroutineScope, onCapture: (ScanResult) -> Unit) {
    init {
        scope.launch {
            scanner.scans.collect { onCapture(if (it is ScanResult.Code && it.value.isEmpty()) ScanResult.Failure else it) }
        }
    }
}

/**
 * Manual entry (FEAT-002 TD, ManualEntryInput): the typed value is digits only, at most 13. It is handed off only by
 * [submit], and only when it is exactly 13 digits (C2, C3); nothing is padded or converted.
 */
class ManualEntryInput(private val onCapture: (ScanResult) -> Unit) {
    private val _value = MutableStateFlow("")
    val value: StateFlow<String> = _value

    fun onChange(text: String) {
        _value.value = text.filter { it in '0'..'9' }.take(CODE_LENGTH)
    }

    fun clear() { _value.value = "" }

    /** Returns true when the value was handed off; otherwise nothing happens and the entry stays as typed. */
    fun submit(): Boolean {
        val code = _value.value
        if (code.length != CODE_LENGTH) return false
        onCapture(ScanResult.Code(code))
        return true
    }

    private companion object { const val CODE_LENGTH = 13 }
}
