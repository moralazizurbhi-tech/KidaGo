package com.afede.kidago.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** FEAT-001 pipeline on the device: real files, real Room store, real coordinator. Access is faked (granted). */
@RunWith(AndroidJUnit4::class)
class CatalogSyncDeviceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var store: LocalCatalogStore
    private lateinit var folder: File
    private lateinit var sync: CatalogSyncCoordinator
    private lateinit var query: CatalogQueryFacade

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        store = LocalCatalogStore(db)
        folder = File(context.cacheDir, "afede-kidago-test").apply { deleteRecursively() } // not created: detection must do it
        sync = CatalogSyncCoordinator(
            guard = FileAccessGuard({ true }, {}),
            detector = CatalogFileDetector(),
            importer = CatalogImporter { store.replaceAll(it) },
            cleanup = CatalogFileCleanup(),
            catalogFolder = { folder },
        )
        query = CatalogQueryFacade(sync.state, store::contains)
    }

    @After
    fun tearDown() {
        db.close()
        folder.deleteRecursively()
    }

    private fun write(name: String, vararg lines: String) = File(folder, name).apply { writeText(lines.joinToString("\n", postfix = "\n")) }

    @Test
    fun fullCycle_replacesCatalog_cleansUp_andKeepsLastGoodCatalogOnFailure() = runBlocking {
        sync.onDetectionOpportunity() // C16: folder created silently, nothing imported
        assertTrue(folder.isDirectory)
        assertEquals(SyncState.UpToDate(null), sync.state.value)

        write("product-catalog-2026-01-01.txt", "1111111111111", "2222222222222")
        write("product-catalog-2026-02-01.txt", "3333333333333", "4444444444444")
        File(folder, "notes.txt").writeText("keep me")
        sync.onDetectionOpportunity()

        assertTrue(sync.state.value is SyncState.UpToDate && (sync.state.value as SyncState.UpToDate).completedAt != null)
        assertEquals(LookupResult.Found, query.lookup("3333333333333")) // C11: newest file's records
        assertEquals(LookupResult.NotFound, query.lookup("1111111111111")) // older file never imported
        assertEquals(listOf("notes.txt"), folder.list()!!.toList()) // C12: catalog files gone, others untouched

        write("product-catalog-2026-03-01.txt", "5555555555555", "not-a-barcode")
        sync.onDetectionOpportunity()

        val error = sync.state.value as SyncState.Error // C5, C6
        assertEquals(2, error.diagnostics.single().lineNumber)
        assertTrue(File(folder, "product-catalog-2026-03-01.txt").exists()) // C13: nothing deleted
        assertEquals(LookupResult.Found, query.lookup("3333333333333")) // previous catalog intact
        assertEquals(LookupResult.NotFound, query.lookup("5555555555555"))

        write("product-catalog-2026-03-01.txt", "5555555555555") // C8: re-sent file, retried automatically
        sync.onDetectionOpportunity()
        assertTrue(sync.state.value is SyncState.UpToDate)
        assertEquals(LookupResult.Found, query.lookup("5555555555555"))
        assertEquals(LookupResult.NotFound, query.lookup("3333333333333")) // C2: full replace
        assertFalse(File(folder, "product-catalog-2026-03-01.txt").exists())
    }

    @Test
    fun largeCatalogImportsOnDevice() = runBlocking {
        folder.mkdirs()
        val lines = (0 until 200_000).map { (1_000_000_000_000L + it).toString() }
        File(folder, "product-catalog-2026-04-01.txt").writeText(lines.joinToString("\n"))
        sync.onDetectionOpportunity()
        assertTrue(sync.state.value is SyncState.UpToDate)
        assertEquals(LookupResult.Found, query.lookup(lines.last()))
        assertEquals(LookupResult.NotFound, query.lookup("9999999999999"))
    }
}
