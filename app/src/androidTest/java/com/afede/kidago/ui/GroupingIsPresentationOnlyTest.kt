package com.afede.kidago.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.afede.kidago.data.AppDatabase
import com.afede.kidago.data.ArchiveReason
import com.afede.kidago.data.ListFileMirror
import com.afede.kidago.data.LookupResult
import com.afede.kidago.data.SyncState
import com.afede.kidago.data.TodaysListEntryStore
import com.afede.kidago.scanner.FakeBarcodeScanner
import com.afede.kidago.scanner.ScanResult
import com.afede.kidago.ui.products.ProductosListViewModel
import com.afede.kidago.ui.products.ProductosScreen
import com.afede.kidago.ui.scan.EscanearScreen
import com.afede.kidago.ui.scan.EscanearViewModel
import com.afede.kidago.ui.shell.AppShell
import com.afede.kidago.ui.theme.KidaGoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** TASK-053: the 1-6-6 grouping appears on screen while the stored list, `scanned-list.txt` and the archive carry digits only. */
class GroupingIsPresentationOnlyTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val code = "8412345678905"
    private lateinit var db: AppDatabase
    private lateinit var list: TodaysListEntryStore
    private lateinit var file: File
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val scanner = FakeBarcodeScanner()
    private val sync = MutableStateFlow<SyncState>(SyncState.UpToDate(null))

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        list = TodaysListEntryStore(db)
        file = File(rule.activity.cacheDir, "scanned-list-${System.nanoTime()}.txt")
        val mirror = ListFileMirror({ file }, list.entries, { true }, { list.archiveAndClear(ArchiveReason.EXPORT) }).also { it.start(scope) }
        runBlocking { mirror.onLaunchOrResume() }
        rule.setContent {
            KidaGoTheme {
                val products: ProductosListViewModel = viewModel { ProductosListViewModel(list.entries, list.total, list::remove) }
                val productos by products.state.collectAsState()
                val escanear: EscanearViewModel = viewModel {
                    EscanearViewModel(scanner, sync, { LookupResult.Found }, list.entries, list::addOrAccumulate, list::applyQuantity)
                }
                val state by escanear.state.collectAsState()
                val typed by escanear.typed.collectAsState()
                AppShell(
                    productsBadge = productos?.total ?: 0,
                    scanScreen = { EscanearScreen(state, typed, escanear::onTyped, escanear::onSubmit, escanear::onQuantitySubmit, onFinish = escanear::onFinish) },
                    productsScreen = { ProductosScreen(productos, products.confirmation, products.removal::onLongPress, removalState = products.removal.state.collectAsState().value) },
                )
            }
        }
    }

    @After
    fun tearDown() {
        rule.activityRule.scenario.close()
        scope.cancel()
        db.close()
        file.delete()
    }

    private fun lines() = file.readLines().filter { it.isNotBlank() }

    @Test
    fun theScreensShowTheGroupedCodeWhileEveryStoredCopyIsDigitsOnly() {
        scanner.emit(ScanResult.Code(code))
        rule.waitUntil(5_000) { runBlocking { db.todaysListDao().find(code) } != null }
        rule.waitForIdle()

        rule.onNodeWithText("8 412345 678905", useUnmergedTree = true).assertIsDisplayed() // Escanear, under the illustration
        rule.onNodeWithText("Productos").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("8 412345 678905").assertIsDisplayed() // Productos' row

        assertEquals(listOf(code), runBlocking { db.todaysListDao().entries() }.map { it.code }) // the store
        rule.waitUntil(5_000) { file.exists() && lines().isNotEmpty() }
        assertEquals(listOf("$code,1"), lines()) // scanned-list.txt: EAN-13,quantity

        runBlocking { list.archiveAndClear(ArchiveReason.MANUAL) } // the archive
        val archived = runBlocking { db.todaysListDao().history().flatMap { db.todaysListDao().historyEntries(it.id) } }
        assertTrue(archived.isNotEmpty() && archived.all { it.code.matches(Regex("\\d{13}")) })
    }
}
