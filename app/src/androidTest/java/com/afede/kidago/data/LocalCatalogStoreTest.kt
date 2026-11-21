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
        store.replaceAll(listOf("0012345678905", "4006381333931").map(::CatalogItem))
        assertTrue(store.find("0012345678905") != null)
        assertTrue(store.find("4006381333931") != null)
        assertFalse(store.find("12345678905") != null) // no padding or conversion
        assertFalse(store.find("4006381333900") != null)
    }

    @Test
    fun replaceFullyReplacesThePreviousCatalog() = runBlocking {
        store.replaceAll(listOf("A", "B").map(::CatalogItem))
        store.replaceAll(listOf("B", "C").map(::CatalogItem))
        assertFalse(store.find("A") != null)
        assertTrue(store.find("B") != null)
        assertTrue(store.find("C") != null)
    }

    @Test
    fun failedReplaceIsNeverPartiallyVisible() = runBlocking {
        store.replaceAll(listOf("OLD").map(::CatalogItem))
        val failing = sequence {
            yield("N1"); yield("N2")
            error("boom")
        }.asIterable()
        try {
            store.replaceAll(failing.map(::CatalogItem))
            fail("expected failure")
        } catch (_: IllegalStateException) {
        }
        assertTrue(store.find("OLD") != null)
        assertFalse(store.find("N1") != null)
    }

    @Test
    fun largeCatalogReplaceAndDuplicatesAreHandled() = runBlocking {
        store.replaceAll(((0 until 12_000).map { "%013d".format(it) } + "0000000000005").map(::CatalogItem))
        assertTrue(store.find("%013d".format(11_999)) != null)
        assertFalse(store.find("%013d".format(12_000)) != null)
    }

    @Test
    fun catalogSurvivesReopen() = runBlocking {
        val file = "catalog-restart-test.db"
        context.deleteDatabase(file)
        var d = Room.databaseBuilder(context, AppDatabase::class.java, file).build()
        LocalCatalogStore(d).replaceAll(listOf("A").map(::CatalogItem))
        d.close()
        d = Room.databaseBuilder(context, AppDatabase::class.java, file).build()
        assertTrue(LocalCatalogStore(d).find("A") != null)
        d.close()
        context.deleteDatabase(file)
        Unit
    }
}
