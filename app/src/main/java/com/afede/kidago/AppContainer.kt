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
import com.afede.kidago.data.ArchiveReason
import com.afede.kidago.data.ListFileMirror
import com.afede.kidago.data.LocalCatalogStore
import com.afede.kidago.data.SettingsStore
import com.afede.kidago.data.TodaysListEntryStore
import com.afede.kidago.scanner.BarcodeScanner
import com.afede.kidago.scanner.EastaeonBarcodeScanner
import com.afede.kidago.ui.scan.AndroidOutcomeCues
import com.afede.kidago.ui.PhysicalKeyboardProbe
import com.afede.kidago.ui.scan.OutcomeFeedbackTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Manual dependency injection: the single place that picks implementations. */
class AppContainer(
    context: Context,
    val scanner: BarcodeScanner = EastaeonBarcodeScanner(context.applicationContext),
) {
    val database: AppDatabase =
        Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "kidago.db").build()
    val fileAccess = FileAccessGuard.forContext(context)
    val settings = SettingsStore(context)
    val keyboardProbe = PhysicalKeyboardProbe.forContext(context)
    val catalog = LocalCatalogStore(database)
    val todaysList = TodaysListEntryStore(database)
    val catalogSync = CatalogSyncCoordinator(
        guard = fileAccess,
        detector = CatalogFileDetector(),
        importer = CatalogImporter { catalog.replaceAll(it) },
        cleanup = CatalogFileCleanup(),
        catalogFolder = { File(settings.settings.value.catalogFolder) },
        hasCatalog = catalog::hasCatalog,
    )
    val outcomeFeedback = OutcomeFeedbackTrigger({ settings.settings.value }, AndroidOutcomeCues(context))
    val catalogQuery = CatalogQueryFacade(catalogSync.state, catalog::contains)

    /** Outlives any activity: a sync must not be cancelled because the screen was rotated or closed. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // The desktop tool reads this file from the fixed `<root>/afede/kidago/`, so it does not follow the catalog-folder setting.
    val listMirror = ListFileMirror(
        file = { File(SettingsStore.defaultCatalogFolder(), ListFileMirror.FILE_NAME) },
        entries = todaysList.entries,
        hasAccess = fileAccess::isGranted,
        archiveForExport = { todaysList.archiveAndClear(ArchiveReason.EXPORT) },
    ).also { it.start(appScope) }
}
