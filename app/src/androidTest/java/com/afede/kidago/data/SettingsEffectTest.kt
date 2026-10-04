package com.afede.kidago.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * FEAT-004's settings acting on the other Features (TASK-029): the catalog folder is read at every detection, wired the
 * way AppContainer wires it, so a change in Ajustes is used by the next one with no restart.
 */
@RunWith(AndroidJUnit4::class)
class SettingsEffectTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefsName = "settings-effect-test"
    private lateinit var db: AppDatabase
    private lateinit var first: File
    private lateinit var second: File

    @Before
    fun setUp() {
        context.deleteSharedPreferences(prefsName)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        first = File(context.cacheDir, "settings-effect-a").apply { deleteRecursively(); mkdirs() }
        second = File(context.cacheDir, "settings-effect-b").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun tearDown() {
        db.close()
        first.deleteRecursively()
        second.deleteRecursively()
        context.deleteSharedPreferences(prefsName)
    }

    @Test
    fun aChangedCatalogFolderIsUsedByTheNextDetection() = runBlocking {
        val settings = SettingsStore(context, first.path, prefsName)
        val catalog = LocalCatalogStore(db)
        val sync = CatalogSyncCoordinator(
            guard = FileAccessGuard({ true }, {}),
            detector = CatalogFileDetector(),
            importer = CatalogImporter { catalog.replaceAll(it) },
            cleanup = CatalogFileCleanup(),
            catalogFolder = { File(settings.settings.value.catalogFolder) }, // as in AppContainer
        )
        File(second, "product-catalog-2026-10-04.txt").writeText("1111111111111\n")

        sync.onDetectionOpportunity() // the default folder is empty
        assertEquals(false, catalog.contains("1111111111111"))

        assertEquals(true, settings.setCatalogFolder(second.path))
        sync.onDetectionOpportunity()
        assertEquals(true, catalog.contains("1111111111111"))
        assertEquals(emptyList<String>(), second.list()!!.toList()) // imported, then cleaned up from the new folder
    }
}
