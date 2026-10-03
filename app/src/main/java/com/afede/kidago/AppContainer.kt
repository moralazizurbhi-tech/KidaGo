package com.afede.kidago

import android.content.Context
import androidx.room.Room
import com.afede.kidago.data.AppDatabase
import com.afede.kidago.data.CatalogFileCleanup
import com.afede.kidago.data.CatalogFileDetector
import com.afede.kidago.data.CatalogImporter
import com.afede.kidago.data.CatalogQueryFacade
import com.afede.kidago.data.CatalogSyncCoordinator
import com.afede.kidago.data.FileAccessGuard
import com.afede.kidago.data.LocalCatalogStore
import com.afede.kidago.data.SettingsStore
import com.afede.kidago.data.TodaysListEntryStore
import com.afede.kidago.scanner.BarcodeScanner
import com.afede.kidago.scanner.FakeBarcodeScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Manual dependency injection: the single place that picks implementations. */
class AppContainer(
    context: Context,
    val scanner: BarcodeScanner = FakeBarcodeScanner(), // swapped for the vendor SDK implementation later
) {
    val database: AppDatabase =
        Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "kidago.db").build()
    val fileAccess = FileAccessGuard.forContext(context)
    val settings = SettingsStore(context)
    val catalog = LocalCatalogStore(database)
    val todaysList = TodaysListEntryStore(database)
    val catalogSync = CatalogSyncCoordinator(
        guard = fileAccess,
        detector = CatalogFileDetector(),
        importer = CatalogImporter { catalog.replaceAll(it) },
        cleanup = CatalogFileCleanup(),
        catalogFolder = { File(settings.settings.value.catalogFolder) },
    )
    val catalogQuery = CatalogQueryFacade(catalogSync.state, catalog::contains)

    /** Outlives any activity: a sync must not be cancelled because the screen was rotated or closed. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
