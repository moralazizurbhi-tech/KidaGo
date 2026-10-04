package com.afede.kidago.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.afede.kidago.data.AppDatabase
import com.afede.kidago.data.ArchiveReason
import com.afede.kidago.data.LookupResult
import com.afede.kidago.data.SyncState
import com.afede.kidago.data.TodaysListEntryStore
import com.afede.kidago.scanner.FakeBarcodeScanner
import com.afede.kidago.scanner.ScanResult
import com.afede.kidago.ui.catalog.CatalogStatusStrip
import com.afede.kidago.ui.products.ProductosListViewModel
import com.afede.kidago.ui.products.ProductosScreen
import com.afede.kidago.ui.scan.EscanearScreen
import com.afede.kidago.ui.scan.EscanearViewModel
import com.afede.kidago.ui.shell.AppShell
import com.afede.kidago.ui.theme.KidaGoTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The screens wired together as MainActivity wires them, over a real list store: the live tab badge, the warning strip on
 * every screen, and both reset paths leaving Escanear and Productos consistent (TASK-029).
 */
class CrossScreenIntegrationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val known = listOf("1111111111111", "2222222222222")
    private lateinit var db: AppDatabase
    private lateinit var list: TodaysListEntryStore
    private val scanner = FakeBarcodeScanner()
    private val sync = MutableStateFlow<SyncState>(SyncState.UpToDate(null))

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        list = TodaysListEntryStore(db)
        rule.setContent {
            KidaGoTheme {
                val products: ProductosListViewModel = viewModel { ProductosListViewModel(list.entries, list.total, list::remove) }
                val productos by products.state.collectAsState()
                val escanear: EscanearViewModel = viewModel {
                    EscanearViewModel(
                        scanner, sync,
                        { if (it in known) LookupResult.Found else LookupResult.NotFound },
                        list.entries, list::addOrAccumulate, list::applyQuantity,
                    )
                }
                val capture by escanear.state.collectAsState()
                val typed by escanear.typed.collectAsState()
                val syncState by sync.collectAsState()
                AppShell(
                    productsBadge = productos?.total ?: 0,
                    headerContent = { CatalogStatusStrip(syncState, {}) },
                    scanScreen = { EscanearScreen(capture, typed, escanear::onTyped, escanear::onSubmit, escanear::onQuantitySubmit, syncing = syncState is SyncState.Syncing, onFinish = escanear::onFinish) },
                    productsScreen = { ProductosScreen(productos, products.confirmation, products.removal::onLongPress, removalState = products.removal.state.collectAsState().value) },
                )
            }
        }
    }

    @After
    fun tearDown() {
        rule.activityRule.scenario.close() // stop the ViewModels' coroutines before the database goes away
        db.close()
    }

    private fun scan(code: String) { scanner.emit(ScanResult.Code(code)); rule.waitForIdle() }
    private fun tab(label: String) = rule.onNodeWithText(label)
    private fun noOutcome() {
        for (tag in listOf("scan-outcome-found", "scan-outcome-notfound", "scan-outcome-error")) rule.onNodeWithTag(tag).assertDoesNotExist()
    }

    @Test
    fun theTabBadgeAndTheProductosHeaderCountBothEqualTheListTotal() {
        scan(known[0]); scan(known[0]); scan(known[1])
        rule.waitUntil(5_000) { runBlocking { db.todaysListDao().entries().sumOf { it.quantity } } == 3 }
        rule.waitForIdle()
        rule.onNodeWithTag("tab-badge", useUnmergedTree = true).assertIsDisplayed().assertTextEquals("3")
        tab("Productos").performClick()
        rule.onNodeWithTag("productos-count").assertTextEquals("3")
    }

    @Test
    fun theMissingAccessWarningShowsOnEveryScreenAndScanningStillWorks() {
        sync.value = SyncState.NoFileAccess
        rule.waitForIdle()
        rule.onNodeWithTag("strip-noaccess").assertIsDisplayed() // Escanear
        scan(known[0])
        rule.waitUntil(5_000) { runBlocking { db.todaysListDao().find(known[0]) } != null } // scanning is not blocked
        tab("Productos").performClick()
        rule.onNodeWithTag("strip-noaccess").assertIsDisplayed()
        rule.onNodeWithContentDescription("Ajustes").performClick()
        rule.onNodeWithTag("strip-noaccess").assertIsDisplayed()
    }

    private fun bothScreensAreResetAfter(reason: ArchiveReason) {
        scan(known[0]); scan(known[1])
        rule.waitUntil(5_000) { runBlocking { db.todaysListDao().entries().size } == 2 }
        rule.onNodeWithTag("scan-outcome-found").assertIsDisplayed()

        runBlocking { list.archiveAndClear(reason) }
        rule.waitUntil(5_000) { runBlocking { db.todaysListDao().entries().isEmpty() } }
        rule.waitForIdle()

        noOutcome() // Escanear is back to Idle
        rule.onNodeWithText("Introduce o escanea un código", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("tab-badge", useUnmergedTree = true).assertDoesNotExist() // badge back to 0
        tab("Productos").performClick()
        rule.onNodeWithTag("productos-empty").assertIsDisplayed()
        rule.onNodeWithTag("productos-count").assertTextEquals("0")
    }

    @Test
    fun aManualClearLeavesEscanearIdleAndProductosEmpty() = bothScreensAreResetAfter(ArchiveReason.MANUAL)

    @Test
    fun anExportResetLeavesEscanearIdleAndProductosEmpty() = bothScreensAreResetAfter(ArchiveReason.EXPORT)
}
