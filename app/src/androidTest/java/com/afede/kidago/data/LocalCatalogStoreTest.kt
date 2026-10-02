package com.afede.kidago.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalCatalogStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var store: LocalCatalogStore

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        store = LocalCatalogStore(db)
    }

    @After
    fun close() = db.close()

    @Test
    fun lookupFindsOnlyPresentBarcodes_exactMatch() = runBlocking {
        store.replaceAll(listOf("0012345678905", "4006381333931"))
        assertTrue(store.contains("0012345678905"))
        assertTrue(store.contains("4006381333931"))
        assertFalse(store.contains("12345678905")) // no padding or conversion
        assertFalse(store.contains("4006381333900"))
    }

    @Test
    fun replaceFullyReplacesThePreviousCatalog() = runBlocking {
        store.replaceAll(listOf("A", "B"))
        store.replaceAll(listOf("B", "C"))
        assertFalse(store.contains("A"))
        assertTrue(store.contains("B"))
        assertTrue(store.contains("C"))
    }

    @Test
    fun failedReplaceIsNeverPartiallyVisible() = runBlocking {
        store.replaceAll(listOf("OLD"))
        val failing = sequence {
            yield("N1"); yield("N2")
            error("boom")
        }.asIterable()
        try {
            store.replaceAll(failing)
            fail("expected failure")
        } catch (_: IllegalStateException) {
        }
        assertTrue(store.contains("OLD"))
        assertFalse(store.contains("N1"))
    }

    @Test
    fun largeCatalogReplaceAndDuplicatesAreHandled() = runBlocking {
        store.replaceAll((0 until 12_000).map { "%013d".format(it) } + "0000000000005")
        assertTrue(store.contains("%013d".format(11_999)))
        assertFalse(store.contains("%013d".format(12_000)))
    }

    @Test
    fun catalogSurvivesReopen() = runBlocking {
        val file = "catalog-restart-test.db"
        context.deleteDatabase(file)
        var d = Room.databaseBuilder(context, AppDatabase::class.java, file).build()
        LocalCatalogStore(d).replaceAll(listOf("A"))
        d.close()
        d = Room.databaseBuilder(context, AppDatabase::class.java, file).build()
        assertTrue(LocalCatalogStore(d).contains("A"))
        d.close()
        context.deleteDatabase(file)
        Unit
    }
}
