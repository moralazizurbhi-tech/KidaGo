package com.afede.kidago

import com.afede.kidago.data.AddResult
import com.afede.kidago.data.ListEntry
import com.afede.kidago.data.LookupResult
import com.afede.kidago.data.SyncState
import com.afede.kidago.scanner.ScanResult
import com.afede.kidago.ui.scan.CaptureCoordinator
import com.afede.kidago.ui.scan.CaptureState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureCoordinatorTest {
    private val catalog = mutableSetOf("A", "B")
    private val gates = mutableMapOf<String, CompletableDeferred<Unit>>() // holds a lookup open until released
    private val looked = mutableListOf<String>()
    private val list = MutableStateFlow<List<ListEntry>>(emptyList())
    private val sync = MutableStateFlow<SyncState>(SyncState.UpToDate(null))
    private var seq = 0L
    private val outcomes = mutableListOf<CaptureState>()

    private fun qty(code: String) = list.value.firstOrNull { it.code == code }?.quantity

    private fun TestScope.coordinator() = CaptureCoordinator(
        onOutcome = { outcomes += it },
        scope = backgroundScope,
        syncState = sync,
        lookup = { code ->
            looked += code
            gates[code]?.await()
            if (code in catalog) LookupResult.Found else LookupResult.NotFound
        },
        entries = list,
        addOrAccumulate = { code ->
            val before = qty(code) ?: 0
            list.value = if (before == 0) list.value + ListEntry(++seq, code, 1) else list.value.map { if (it.code == code) it.copy(quantity = before + 1) else it }
            AddResult(before, before + 1, before > 0)
        },
        applyQuantity = { code, before, n ->
            val exists = qty(code) != null
            if (exists) list.value = list.value.map { if (it.code == code) it.copy(quantity = before + n) else it }
            exists
        },
    )

    private fun entry(code: String, quantity: Int) = ListEntry(++seq, code, quantity)
    private fun code(c: String) = ScanResult.Code(c)

    @Test
    fun typingMovesBetweenIdleAndTypingButNeverDisturbsAnOutcome() = runTest {
        val c = coordinator()
        c.onTyping(true)
        assertEquals(CaptureState.Typing, c.state.value)
        c.onTyping(false)
        assertEquals(CaptureState.Idle, c.state.value)
        c.onCapture(code("A")); runCurrent()
        c.onTyping(true)
        assertEquals(CaptureState.Found("A", 1, false), c.state.value)
    }

    @Test
    fun matchAddsNewEntryAtOneAndRescanReportsAccumulatedTotal() = runTest {
        list.value = listOf(entry("A", 3))
        val c = coordinator()
        c.onCapture(code("B")); runCurrent()
        assertEquals(CaptureState.Found("B", 1, false), c.state.value)
        c.onCapture(code("A")); runCurrent()
        assertEquals(CaptureState.Found("A", 4, true), c.state.value)
        assertEquals(4, qty("A"))
        assertEquals(2, list.value.size) // never a second line for A
    }

    @Test
    fun noMatchIsNotFoundAndLeavesTheListUnchanged() = runTest {
        val c = coordinator()
        c.onCapture(code("Z")); runCurrent()
        assertEquals(CaptureState.NotFound("Z"), c.state.value)
        assertEquals(emptyList<ListEntry>(), list.value)
    }

    @Test
    fun readFailureIsErrorNeverNotFoundAndLooksNothingUp() = runTest {
        val c = coordinator()
        c.onCapture(ScanResult.Failure); runCurrent()
        assertEquals(CaptureState.Error, c.state.value)
        assertEquals(emptyList<String>(), looked)
    }

    @Test
    fun captureGoesStraightToSearchingWhileResolving() = runTest {
        gates["A"] = CompletableDeferred()
        val c = coordinator()
        c.onCapture(code("A")); runCurrent()
        assertEquals(CaptureState.Searching, c.state.value)
        gates.getValue("A").complete(Unit); runCurrent()
        assertEquals(CaptureState.Found("A", 1, false), c.state.value)
    }

    @Test
    fun newCaptureSupersedesAnInFlightOneWhichNeverTouchesTheList() = runTest {
        gates["A"] = CompletableDeferred()
        val c = coordinator()
        c.onCapture(code("A")); runCurrent()
        c.onCapture(code("B")); runCurrent()
        assertEquals(CaptureState.Found("B", 1, false), c.state.value)
        gates.getValue("A").complete(Unit); runCurrent()
        assertEquals(CaptureState.Found("B", 1, false), c.state.value)
        assertEquals(null, qty("A"))
    }

    @Test
    fun newCaptureSupersedesAShowingOutcomeWithNoDismissStep() = runTest {
        gates["B"] = CompletableDeferred()
        val c = coordinator()
        c.onCapture(code("A")); runCurrent()
        c.onCapture(code("B")); runCurrent()
        assertEquals(CaptureState.Searching, c.state.value)
        c.onCapture(ScanResult.Failure); runCurrent()
        assertEquals(CaptureState.Error, c.state.value)
        c.onCapture(code("Z")); runCurrent()
        assertEquals(CaptureState.NotFound("Z"), c.state.value)
    }

    @Test
    fun captureIsRefusedWhileSyncingAndAcceptedAgainAfterwards() = runTest {
        val c = coordinator()
        sync.value = SyncState.Syncing(0.5f)
        c.onCapture(code("A")); c.onCapture(ScanResult.Failure); runCurrent()
        assertEquals(CaptureState.Idle, c.state.value)
        assertEquals(emptyList<String>(), looked)
        sync.value = SyncState.UpToDate(null)
        c.onCapture(code("A")); runCurrent()
        assertEquals(CaptureState.Found("A", 1, false), c.state.value)
    }

    @Test
    fun finishScanFromAnyOutcomeReturnsToIdleWithoutTouchingTheList() = runTest {
        list.value = listOf(entry("A", 3))
        val c = coordinator()
        c.onCapture(code("Z")); runCurrent()
        c.finishScan(); runCurrent()
        assertEquals(CaptureState.Idle, c.state.value)
        c.onCapture(ScanResult.Failure); runCurrent()
        c.finishScan("5"); runCurrent() // Cantidad applies on Found only
        assertEquals(CaptureState.Idle, c.state.value)
        c.onCapture(code("A")); runCurrent()
        c.finishScan(); runCurrent() // nothing typed: the +1 stays
        assertEquals(CaptureState.Idle, c.state.value)
        assertEquals(listOf(4), list.value.map { it.quantity })
    }

    @Test
    fun finishScanAppliesAPendingCantidadFirst() = runTest {
        list.value = listOf(entry("A", 3))
        val c = coordinator()
        c.onCapture(code("A")); runCurrent()
        c.finishScan("5"); runCurrent()
        assertEquals(8, qty("A"))
        assertEquals(CaptureState.Idle, c.state.value)
    }

    @Test
    fun finishScanIgnoresAnInvalidCantidadAndDoesNothingOutsideAnOutcome() = runTest {
        list.value = listOf(entry("A", 3))
        val c = coordinator()
        c.finishScan("5"); runCurrent() // Idle: no-op
        c.onTyping(true)
        c.finishScan("5"); runCurrent()
        assertEquals(CaptureState.Typing, c.state.value)
        c.onTyping(false)
        c.onCapture(code("A")); runCurrent()
        c.finishScan("0"); runCurrent()
        assertEquals(4, qty("A"))
        assertEquals(CaptureState.Idle, c.state.value)
    }

    @Test
    fun cantidadAddsToTheQuantityBeforeThisScanAndResubmitReplaces() = runTest {
        list.value = listOf(entry("A", 3))
        val c = coordinator()
        c.onCapture(code("A")); runCurrent() // 3 -> 4
        c.onQuantitySubmit("5"); runCurrent()
        assertEquals(8, qty("A"))
        assertEquals(CaptureState.Found("A", 8, true), c.state.value)
        c.onQuantitySubmit("2"); runCurrent()
        assertEquals(5, qty("A")) // 3 + 2, not 8 + 2
        c.onCapture(code("B")); runCurrent()
        c.onQuantitySubmit("12"); runCurrent()
        assertEquals(12, qty("B"))
        c.onQuantitySubmit("99"); runCurrent()
        assertEquals(99, qty("B"))
    }

    @Test
    fun cantidadOutsideOneTo99OrNotAWholeNumberKeepsTheDefault() = runTest {
        val c = coordinator()
        c.onCapture(code("A")); runCurrent()
        for (bad in listOf("0", "100", "abc", "", "-3", "+5", "1.5", " ")) c.onQuantitySubmit(bad)
        runCurrent()
        assertEquals(1, qty("A"))
        assertEquals(CaptureState.Found("A", 1, false), c.state.value)
    }

    @Test
    fun cantidadNoLongerAppliesOnceSupersededOrOutsideFound() = runTest {
        gates["B"] = CompletableDeferred()
        val c = coordinator()
        c.onQuantitySubmit("5"); runCurrent() // Idle: nothing to apply to
        c.onCapture(code("A")); runCurrent()
        c.onCapture(code("B")); runCurrent() // Searching B
        c.onQuantitySubmit("5"); runCurrent()
        assertEquals(1, qty("A"))
        gates.getValue("B").complete(Unit); runCurrent()
        c.onQuantitySubmit("7"); runCurrent()
        assertEquals(1, qty("A")) // A's input is gone for good
        assertEquals(7, qty("B"))
    }

    @Test
    fun removalOrClearBehindAFoundOutcomeReturnsToIdleAndDropsCantidad() = runTest {
        val c = coordinator()
        c.onCapture(code("A")); runCurrent()
        list.value = emptyList() // the entry was removed, or the list cleared
        runCurrent()
        assertEquals(CaptureState.Idle, c.state.value)
        c.onQuantitySubmit("5"); runCurrent()
        assertEquals(null, qty("A"))

        c.onCapture(code("B")); runCurrent()
        list.value = list.value.filter { it.code != "Z" } // unrelated change leaves Found alone
        runCurrent()
        assertEquals(CaptureState.Found("B", 1, false), c.state.value)
    }

    @Test
    fun outcomeIsAnnouncedOncePerTransitionNeverForSearchingTypingOrCantidad() = runTest {
        val c = coordinator()
        c.onTyping(true)
        c.onCapture(code("A")); runCurrent()
        c.onQuantitySubmit("3"); runCurrent()
        c.onCapture(code("Z")); runCurrent()
        c.onCapture(ScanResult.Failure)
        assertEquals(listOf(CaptureState.Found("A", 1, false), CaptureState.NotFound("Z"), CaptureState.Error), outcomes)
    }
}
