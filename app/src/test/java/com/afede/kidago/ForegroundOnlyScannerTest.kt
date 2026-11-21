package com.afede.kidago

import com.afede.kidago.scanner.FakeBarcodeScanner
import com.afede.kidago.scanner.ForegroundOnlyScanner
import com.afede.kidago.scanner.ScanResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** FEAT-002 C21: a scan made while the app is in the background never reaches it. */
@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundOnlyScannerTest {
    @Test
    fun scansReachTheAppOnlyInTheForeground() = runTest(UnconfinedTestDispatcher()) {
        val source = FakeBarcodeScanner()
        val foreground = MutableStateFlow(true)
        val seen = mutableListOf<ScanResult>()
        val job = launch { ForegroundOnlyScanner(source, foreground).scans.collect { seen += it } }
        source.emit(ScanResult.Code("1"))
        foreground.value = false
        source.emit(ScanResult.Code("2"))
        foreground.value = true
        source.emit(ScanResult.Code("3"))
        assertEquals(listOf(ScanResult.Code("1"), ScanResult.Code("3")), seen)
        job.cancel()
    }
}
