package com.afede.kidago

import com.afede.kidago.scanner.FakeBarcodeScanner
import com.afede.kidago.scanner.ScanResult
import com.afede.kidago.ui.scan.BarcodeScannerInput
import com.afede.kidago.ui.scan.ManualEntryInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureInputTest {
    private val captures = mutableListOf<ScanResult>()

    // C1: a hardware scan is handed off as soon as it is captured, with no submit step
    @Test
    fun scanIsHandedOffImmediatelyExactlyAsDelivered() {
        val fake = FakeBarcodeScanner()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        BarcodeScannerInput(fake, scope) { captures += it }
        fake.emit(ScanResult.Code("0000000000123"))
        fake.emit(ScanResult.Code("12345")) // no padding, no length rule on scans (C3)
        assertEquals(listOf(ScanResult.Code("0000000000123"), ScanResult.Code("12345")), captures)
        scope.cancel()
    }

    // C8: an unusable read (failure, or an empty code) is a failure, never a code to look up
    @Test
    fun failureAndEmptyCodeAreReportedAsFailure() {
        val fake = FakeBarcodeScanner()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        BarcodeScannerInput(fake, scope) { captures += it }
        fake.emit(ScanResult.Failure)
        fake.emit(ScanResult.Code(""))
        assertEquals(listOf(ScanResult.Failure, ScanResult.Failure), captures)
        scope.cancel()
    }

    // C2: typing hands nothing off
    @Test
    fun typingNeverHandsOffUntilSubmit() {
        val manual = ManualEntryInput { captures += it }
        manual.onChange("1234567890123")
        assertEquals("1234567890123", manual.value.value)
        assertTrue(captures.isEmpty())
    }

    // C3: digits only, never more than 13
    @Test
    fun fieldKeepsOnlyDigitsAndAtMost13() {
        val manual = ManualEntryInput { captures += it }
        manual.onChange("12a-3 4")
        assertEquals("1234", manual.value.value)
        manual.onChange("12345678901234567")
        assertEquals("1234567890123", manual.value.value)
    }

    // C3: exactly 13 digits submits, leading zeros kept, nothing converted
    @Test
    fun exactly13DigitsSubmitsAsTyped() {
        val manual = ManualEntryInput { captures += it }
        manual.onChange("0000000000123")
        assertTrue(manual.submit())
        assertEquals(listOf<ScanResult>(ScanResult.Code("0000000000123")), captures)
    }

    // C3: a short code is submitted as typed (never padded); an empty entry is not submittable
    @Test
    fun shortEntryIsSubmittedAsTypedAndIsNotPadded() {
        val manual = ManualEntryInput { captures += it }
        manual.onChange("123")
        assertTrue(manual.submit())
        assertEquals(listOf<ScanResult>(ScanResult.Code("123")), captures)
        assertFalse(ManualEntryInput { captures += it }.submit()) // empty
    }
}
