package com.afede.kidago

import com.afede.kidago.scanner.BarcodeScanner
import com.afede.kidago.scanner.FakeBarcodeScanner
import com.afede.kidago.scanner.ScanResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeBarcodeScannerTest {
    @Test
    fun injectedScannerDeliversCodeAsReceivedAndFailureAsFailure() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeBarcodeScanner()
        val scanner: BarcodeScanner = fake // what the app container hands out
        val seen = mutableListOf<ScanResult>()
        val job = launch { scanner.scans.collect { seen += it } }

        fake.emit(ScanResult.Code("0012345678905"))
        fake.emit(ScanResult.Failure)
        job.cancel()

        assertEquals(listOf(ScanResult.Code("0012345678905"), ScanResult.Failure), seen)
    }
}
