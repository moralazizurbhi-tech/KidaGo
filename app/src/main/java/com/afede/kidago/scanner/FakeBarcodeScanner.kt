package com.afede.kidago.scanner

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** Stand-in until the PDA vendor SDK is identified; tests drive it with [emit]. */
class FakeBarcodeScanner : BarcodeScanner {
    private val flow = MutableSharedFlow<ScanResult>(extraBufferCapacity = 16)
    override val scans: Flow<ScanResult> = flow

    fun emit(result: ScanResult) = flow.tryEmit(result)
}
