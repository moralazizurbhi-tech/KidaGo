package com.afede.kidago.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

class CatalogSyncTest {
    @get:Rule val tmp = TemporaryFolder()

    private var access = true
    private var settingsOpened = 0
    private var committed = mutableListOf<List<String>>()
    private val finishedAt = Instant.parse("2026-10-03T10:00:00Z")

    private fun coordinator(importer: CatalogImporter = CatalogImporter { committed += it }) = CatalogSyncCoordinator(
        guard = FileAccessGuard({ access }, { settingsOpened++ }),
        detector = CatalogFileDetector(),
        importer = importer,
        cleanup = CatalogFileCleanup(),
        catalogFolder = { tmp.root },
        now = { finishedAt },
    )

    private fun catalog(name: String, text: String) = File(tmp.root, name).apply { writeText(text) }
    private val good = "1234567890123\n"
    private val bad = "1234567890123\nnope\n"

    @Test
    fun startsUpToDateWithNoCompletionTime() {
        assertEquals(SyncState.UpToDate(null), coordinator().state.value)
    }

    @Test
    fun noFileMeansNoImportAndUnchangedState() = runTest {
        val c = coordinator()
        c.onDetectionOpportunity()
        assertEquals(SyncState.UpToDate(null), c.state.value)
        assertTrue(committed.isEmpty())
    }

    @Test
    fun presentFileIsImportedThenCleanedUpAndTimeRecorded() = runTest {
        val newest = catalog("product-catalog-2026-03-01.txt", good)
        val older = catalog("product-catalog-2026-01-01.txt", good)
        val other = catalog("keep.txt", "x")
        val c = coordinator()
        c.onDetectionOpportunity()
        assertEquals(SyncState.UpToDate(finishedAt), c.state.value)
        assertEquals(1, committed.size)
        assertFalse(newest.exists() || older.exists())
        assertTrue(other.exists())
    }

    @Test
    fun failedImportGoesToErrorWithDiagnosticsAndNeverCleansUp() = runTest {
        val file = catalog("product-catalog-2026-03-01.txt", bad)
        val c = coordinator()
        c.onDetectionOpportunity()
        val state = c.state.value as SyncState.Error
        assertEquals(2, state.diagnostics.single().lineNumber)
        assertTrue(file.exists())
        assertTrue(committed.isEmpty())
    }

    @Test
    fun errorRetriesAutomaticallyAtTheNextOpportunityAndRecovers() = runTest {
        val file = catalog("product-catalog-2026-03-01.txt", bad)
        val c = coordinator()
        c.onDetectionOpportunity()
        assertTrue(c.state.value is SyncState.Error)
        file.writeText(good) // the user re-sends a fixed file
        c.onDetectionOpportunity()
        assertEquals(SyncState.UpToDate(finishedAt), c.state.value)
    }

    @Test
    fun errorStateIsKeptWhenLaterOpportunitiesFindNothing() = runTest {
        catalog("product-catalog-2026-03-01.txt", bad)
        val c = coordinator()
        c.onDetectionOpportunity()
        File(tmp.root, "product-catalog-2026-03-01.txt").delete()
        c.onDetectionOpportunity()
        assertTrue(c.state.value is SyncState.Error)
    }

    @Test
    fun withoutAccessDetectionIsSkippedAndUserIsAskedAtEachCheck() = runTest {
        access = false
        catalog("product-catalog-2026-03-01.txt", good)
        val c = coordinator()
        c.onDetectionOpportunity()
        c.onDetectionOpportunity()
        assertEquals(SyncState.NoFileAccess, c.state.value)
        assertEquals(2, settingsOpened)
        assertTrue(committed.isEmpty())
    }

    @Test
    fun grantingAccessClearsTheWarningAndImportsAtTheNextCheck() = runTest {
        access = false
        catalog("product-catalog-2026-03-01.txt", good)
        val c = coordinator()
        c.onDetectionOpportunity()
        access = true
        c.onDetectionOpportunity()
        assertEquals(SyncState.UpToDate(finishedAt), c.state.value)
        assertEquals(1, committed.size)
    }

    @Test
    fun grantingAccessWithNothingToImportRestoresThePreviousState() = runTest {
        catalog("product-catalog-2026-03-01.txt", bad)
        val c = coordinator()
        c.onDetectionOpportunity() // Error
        File(tmp.root, "product-catalog-2026-03-01.txt").delete()
        access = false
        c.onDetectionOpportunity()
        assertEquals(SyncState.NoFileAccess, c.state.value)
        access = true
        c.onDetectionOpportunity()
        assertTrue(c.state.value is SyncState.Error)
    }

    @Test
    fun secondOpportunityWhileSyncingDoesNotStartASecondImport() = runBlocking {
        catalog("product-catalog-2026-03-01.txt", good)
        val gate = CompletableDeferred<Unit>()
        var commits = 0
        val c = coordinator(CatalogImporter { commits++; gate.await() })
        val first = launch(Dispatchers.Default) { c.onDetectionOpportunity() }
        assertTrue(c.state.first { it is SyncState.Syncing } is SyncState.Syncing)
        c.onDetectionOpportunity() // returns at once, touches nothing
        assertEquals(1, commits)
        assertEquals(0, settingsOpened)
        gate.complete(Unit)
        first.join()
        assertEquals(SyncState.UpToDate(finishedAt), c.state.value)
        assertEquals(1, commits)
    }

    @Test
    fun progressIsObservableWhileSyncing() = runBlocking {
        catalog("product-catalog-2026-03-01.txt", good.repeat(5))
        val c = coordinator()
        val seen = mutableListOf<SyncState>()
        val collector = launch(Dispatchers.Unconfined) { c.state.collect { seen += it } }
        c.onDetectionOpportunity()
        collector.cancel()
        assertTrue(seen.any { it is SyncState.Syncing && it.progress > 0f })
    }

    @Test
    fun facadeRefusesOnlyWhileSyncingAndNeverConsultsTheStoreThen() = runTest {
        val state = MutableStateFlow<SyncState>(SyncState.Syncing(0.5f))
        var consulted = 0
        val facade = CatalogQueryFacade(state) { consulted++; it == "1234567890123" }
        assertEquals(LookupResult.Unavailable, facade.lookup("1234567890123"))
        assertEquals(0, consulted)
        for (s in listOf(SyncState.UpToDate(null), SyncState.Error(emptyList(), 1), SyncState.NoFileAccess)) {
            state.value = s
            assertEquals(LookupResult.Found, facade.lookup("1234567890123"))
            assertEquals(LookupResult.NotFound, facade.lookup("0000000000000"))
        }
    }
}
