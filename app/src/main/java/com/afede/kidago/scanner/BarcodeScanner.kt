package com.afede.kidago.scanner

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest

/** A hardware capture attempt: the code exactly as received, or a read failure. */
sealed interface ScanResult {
    data class Code(val value: String) : ScanResult
    data object Failure : ScanResult
}

/** Isolates the PDA vendor SDK; one implementation per vendor, chosen in [com.afede.kidago.AppContainer]. */
interface BarcodeScanner {
    val scans: Flow<ScanResult>
}

/**
 * Listens to [source] only while [foreground] is true (FEAT-002 C21). Leaving the foreground cancels the collection, so
 * the vendor receiver is unregistered: a scan made in the background never reaches the app, not even later — Android
 * queues broadcasts for a cached app and would hand them over on its return.
 */
class ForegroundOnlyScanner(source: BarcodeScanner, foreground: Flow<Boolean>) : BarcodeScanner {
    @OptIn(ExperimentalCoroutinesApi::class)
    override val scans: Flow<ScanResult> = foreground.flatMapLatest { if (it) source.scans else emptyFlow() }
}
