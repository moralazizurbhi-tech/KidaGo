package com.afede.kidago

import com.afede.kidago.data.LookupResult
import com.afede.kidago.data.SyncState
import com.afede.kidago.scanner.FakeBarcodeScanner
import com.afede.kidago.scanner.ScanResult
import com.afede.kidago.ui.price.PriceLookupViewModel
import com.afede.kidago.ui.price.PriceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** FEAT-006: the price lookup shows prices, never adds, and only listens while its tab is shown. */
@OptIn(ExperimentalCoroutinesApi::class)
class PriceLookupViewModelTest {
    private val scanner = FakeBarcodeScanner()
    private val sync = MutableStateFlow<SyncState>(SyncState.UpToDate(null))
    private val catalog = mapOf("8410000000009" to 2499L, "8410000000016" to null)
    private val outcomes = mutableListOf<PriceState>()
    private lateinit var vm: PriceLookupViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        vm = PriceLookupViewModel(scanner, sync, { code -> if (code in catalog) LookupResult.Found(catalog[code]) else LookupResult.NotFound }, outcomes::add)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun aScanShowsThePriceOrItsAbsence() {
        vm.setScreenActive(true)
        scanner.emit(ScanResult.Code("8410000000009"))
        assertEquals(PriceState.Found("8410000000009", 2499), vm.state.value)
        scanner.emit(ScanResult.Code("8410000000016"))
        assertEquals(PriceState.Found("8410000000016", null), vm.state.value) // C3: replaces at once
        scanner.emit(ScanResult.Code("1"))
        assertEquals(PriceState.NotFound("1"), vm.state.value)
        vm.onBack()
        scanner.emit(ScanResult.Failure)
        assertEquals(PriceState.Error, vm.state.value)
        assertEquals(4, outcomes.size)
    }

    @Test
    fun notFoundAndErrorTakeNoNewCaptureUntilVolver() {
        vm.setScreenActive(true)
        scanner.emit(ScanResult.Code("1"))
        scanner.emit(ScanResult.Code("8410000000009"))
        vm.onTyped("8410000000009")
        vm.onSubmit()
        assertEquals(PriceState.NotFound("1"), vm.state.value) // C9
        vm.onBack()
        scanner.emit(ScanResult.Failure)
        scanner.emit(ScanResult.Code("8410000000009"))
        assertEquals(PriceState.Error, vm.state.value)
        assertEquals(2, outcomes.size)
    }

    @Test
    fun scansAreIgnoredWhileTheTabIsNotShownOrDuringASync() {
        scanner.emit(ScanResult.Code("8410000000009"))
        assertEquals(PriceState.Idle, vm.state.value) // C4
        vm.setScreenActive(true)
        sync.value = SyncState.Syncing(0.5f)
        scanner.emit(ScanResult.Code("8410000000009"))
        assertEquals(PriceState.Idle, vm.state.value)
    }

    @Test
    fun volverAndLeavingTheTabReturnToTheBase() {
        vm.setScreenActive(true)
        scanner.emit(ScanResult.Code("8410000000009"))
        vm.onBack()
        assertEquals(PriceState.Idle, vm.state.value) // C3
        scanner.emit(ScanResult.Code("8410000000009"))
        vm.setScreenActive(false)
        assertEquals(PriceState.Idle, vm.state.value) // C5
    }

    @Test
    fun aTypedCodeIsLookedUpLikeAScannedOne() {
        vm.setScreenActive(true)
        vm.onTyped("8410000000009")
        vm.onSubmit()
        assertEquals(PriceState.Found("8410000000009", 2499), vm.state.value) // C8
        assertEquals("", vm.typed.value)
    }
}
