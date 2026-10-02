package com.afede.kidago.scanner

import kotlinx.coroutines.flow.Flow

/** A hardware capture attempt: the code exactly as received, or a read failure. */
sealed interface ScanResult {
    data class Code(val value: String) : ScanResult
    data object Failure : ScanResult
}

/** Isolates the PDA vendor SDK; one implementation per vendor, chosen in [com.afede.kidago.AppContainer]. */
interface BarcodeScanner {
    val scans: Flow<ScanResult>
}
