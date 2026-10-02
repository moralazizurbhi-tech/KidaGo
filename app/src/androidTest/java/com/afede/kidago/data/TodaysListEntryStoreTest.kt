package com.afede.kidago.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodaysListEntryStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var store: TodaysListEntryStore

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        store = TodaysListEntryStore(db)
    }

    @After
    fun close() = db.close()

    private fun lines() = runBlocking { store.entries.first().map { it.code to it.quantity } }

    @Test
    fun addThenAccumulateReportsBeforeAfterAndWasExisting() = runBlocking {
        assertEquals(AddResult(0, 1, false), store.addOrAccumulate("0012345678905"))
        assertEquals(AddResult(1, 2, true), store.addOrAccumulate("0012345678905"))
        assertEquals(listOf("0012345678905" to 2), lines())
    }

    @Test
    fun codeMatchedExactlyAsCaptured_leadingZerosKept() = runBlocking {
        store.addOrAccumulate("0000000000017")
        store.addOrAccumulate("17")
        assertEquals(2, lines().size)
    }

    @Test
    fun orderIsInsertionOrderAndStableAcrossQuantityChanges() = runBlocking {
        store.addOrAccumulate("A"); store.addOrAccumulate("B"); store.addOrAccumulate("C")
        store.addOrAccumulate("A")
        store.applyQuantity("B", 0, 7)
        assertEquals(listOf("A" to 2, "B" to 7, "C" to 1), lines())
    }

    @Test
    fun cantidadReplacesTheDefaultPlusOne_andResubmitReplaces() = runBlocking {
        repeat(3) { store.addOrAccumulate("X") }
        val r = store.addOrAccumulate("X") // before = 3
        assertTrue(store.applyQuantity("X", r.quantityBefore, 5))
        assertEquals(listOf("X" to 8), lines())
        assertTrue(store.applyQuantity("X", r.quantityBefore, 2))
        assertEquals(listOf("X" to 5), lines())
    }

    @Test
    fun cantidadOnRemovedEntryIsRefused() = runBlocking {
        store.addOrAccumulate("X")
        store.remove("X")
        assertFalse(store.applyQuantity("X", 0, 4))
        assertEquals(emptyList<Pair<String, Int>>(), lines())
    }

    @Test
    fun totalIsObservableAndZeroWhenEmpty() = runBlocking {
        assertEquals(0, store.total.first())
        store.addOrAccumulate("A"); store.addOrAccumulate("A"); store.addOrAccumulate("B")
        assertEquals(3, store.total.first())
        store.remove("A")
        assertEquals(1, store.total.first())
    }

    @Test
    fun archiveAndClearCopiesEveryEntryThenEmptiesTheList() = runBlocking {
        store.addOrAccumulate("A"); store.addOrAccumulate("B"); store.addOrAccumulate("B")
        assertTrue(store.archiveAndClear(ArchiveReason.EXPORT))
        assertEquals(emptyList<Pair<String, Int>>(), lines())
        val record = store.history().single()
        assertEquals(ArchiveReason.EXPORT, record.reason)
        assertEquals(listOf("A" to 1, "B" to 2), store.historyEntries(record.id).map { it.code to it.quantity })
    }

    @Test
    fun archiveOnEmptyListWritesNothingAndDoesNotFail() = runBlocking {
        assertFalse(store.archiveAndClear(ArchiveReason.MANUAL))
        assertTrue(store.history().isEmpty())
    }

    @Test
    fun archiveFailureLeavesListAndHistoryUnchanged() = runBlocking {
        val failing = TodaysListEntryStore(db) { error("disk full") }
        failing.addOrAccumulate("A")
        try {
            failing.archiveAndClear(ArchiveReason.MANUAL)
            fail("expected failure")
        } catch (_: IllegalStateException) {
        }
        assertEquals(listOf("A" to 1), lines())
        assertTrue(store.history().isEmpty())
    }

    @Test
    fun twoResetsKeepBothRecordsUnchanged() = runBlocking {
        store.addOrAccumulate("A"); store.archiveAndClear(ArchiveReason.EXPORT)
        store.addOrAccumulate("B"); store.archiveAndClear(ArchiveReason.MANUAL)
        val records = store.history()
        assertEquals(2, records.size)
        assertEquals(listOf("A"), store.historyEntries(records[0].id).map { it.code })
        assertEquals(listOf("B"), store.historyEntries(records[1].id).map { it.code })
    }

    @Test
    fun listAndHistorySurviveReopen() = runBlocking {
        val file = "restart-test.db"
        context.deleteDatabase(file)
        var d = Room.databaseBuilder(context, AppDatabase::class.java, file).build()
        var s = TodaysListEntryStore(d)
        s.addOrAccumulate("A"); s.archiveAndClear(ArchiveReason.EXPORT); s.addOrAccumulate("B")
        d.close()
        d = Room.databaseBuilder(context, AppDatabase::class.java, file).build()
        s = TodaysListEntryStore(d)
        assertEquals(listOf("B" to 1), s.entries.first().map { it.code to it.quantity })
        assertEquals(1, s.history().size)
        d.close()
        context.deleteDatabase(file)
        Unit
    }
}
