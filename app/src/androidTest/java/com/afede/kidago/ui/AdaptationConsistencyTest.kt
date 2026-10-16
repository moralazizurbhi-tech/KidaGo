package com.afede.kidago.ui

import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.afede.kidago.data.AppDatabase
import com.afede.kidago.data.SyncState
import com.afede.kidago.data.TodaysListEntryStore
import com.afede.kidago.ui.catalog.CatalogStatusStrip
import com.afede.kidago.ui.components.ConfirmationController
import com.afede.kidago.ui.components.ConfirmationRequest
import com.afede.kidago.ui.products.ProductosListViewModel
import com.afede.kidago.ui.products.ProductosScreen
import com.afede.kidago.ui.scan.CaptureState
import com.afede.kidago.ui.scan.EscanearScreen
import com.afede.kidago.ui.settings.AjustesScreen
import com.afede.kidago.ui.settings.AjustesState
import com.afede.kidago.ui.shell.AppShell
import com.afede.kidago.ui.theme.KidaGoTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * TASK-053: every screen and state together under the PDA's 480x800 at 240 dpi. The shell, the strip and the three screens
 * are wired as MainActivity wires them; all values are design px. Only content is measured against the 32 px margins: the
 * cards of Productos and Ajustes keep the reference's 20 px edge with 32 px of padding inside it.
 */
class AdaptationConsistencyTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var db: AppDatabase
    private lateinit var list: TodaysListEntryStore
    private val capture = mutableStateOf<CaptureState>(CaptureState.Idle)
    private val physicalKeyboard = mutableStateOf(true)
    private val clearConfirmation = ConfirmationController()
    private val settings = AjustesState(soundEnabled = true, vibrationEnabled = false, catalogFolder = "/storage/emulated/0/afede/kidago", version = "0.1.0")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        list = TodaysListEntryStore(db)
        rule.setContent {
            KidaGoTheme {
                val products: ProductosListViewModel = viewModel { ProductosListViewModel(list.entries, list.total, list::remove) }
                val productos by products.state.collectAsState()
                AppShell(
                    productsBadge = productos?.total ?: 0,
                    headerContent = { CatalogStatusStrip(SyncState.UpToDate(null), {}) },
                    scanScreen = { EscanearScreen(capture.value, "", {}, {}, {}, physicalKeyboard = physicalKeyboard.value) },
                    productsScreen = { ProductosScreen(productos, products.confirmation, products.removal::onLongPress, removalState = products.removal.state.collectAsState().value) },
                    settingsScreen = { AjustesScreen(settings, {}, {}, { true }, clearConfirmation, { clearConfirmation.request(ConfirmationRequest("¿Vaciar la lista?", "Vaciar", onConfirm = {})) }, physicalKeyboard = physicalKeyboard.value) },
                )
            }
        }
    }

    @After
    fun tearDown() {
        rule.activityRule.scenario.close()
        db.close()
    }

    private fun b(tag: String) = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
    private val frameRight get() = b("tab-bar").right.value
    private val barTop get() = b("tab-bar").top.value

    /** Content inside the 32 px margins, inside the frame, above the tab bar. */
    private fun assertInsideMargins(vararg tags: String) = tags.forEach { tag ->
        val r = b(tag)
        assertTrue("$tag left ${r.left.value}", r.left.value >= 31.5f)
        assertTrue("$tag right ${r.right.value}", r.right.value <= frameRight - 31.5f)
        assertTrue("$tag top ${r.top.value}", r.top.value >= 0f)
        assertTrue("$tag bottom ${r.bottom.value} vs tab bar $barTop", r.bottom.value <= barTop)
    }

    private fun assertStripVisible() = rule.onNodeWithTag("strip-uptodate").assertIsDisplayed().also { assertInsideMargins("strip-uptodate") }

    @Test
    fun everyStateOfEscanearKeepsTheMarginsHasNothingClippedAndShowsTheStrip() {
        val states = listOf(
            CaptureState.Idle to listOf("scan-input"),
            CaptureState.Searching to listOf("scan-hero"),
            CaptureState.Found("8412345678905", 1, false) to listOf("scan-input", "scan-badge", "scan-cantidad", "scan-listo"),
            CaptureState.NotFound("8412345678905") to listOf("scan-input", "scan-badge", "scan-listo"),
            CaptureState.Error to listOf("scan-badge", "scan-listo"),
        )
        states.forEach { (state, tags) ->
            capture.value = state
            rule.waitForIdle()
            assertInsideMargins(*tags.toTypedArray())
            assertStripVisible()
        }
    }

    @Test
    fun productosKeepsTheMarginsInItsEmptyPopulatedAndConfirmingStates() {
        rule.onNodeWithText("Productos").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("productos-empty").assertIsDisplayed()
        assertInsideMargins("productos-badge"); assertStripVisible()

        runBlocking { listOf("8412345678905", "8412345678912", "8412345678929").forEach { list.addOrAccumulate(it) } }
        rule.waitForIdle()
        assertInsideMargins("productos-badge", "productos-row-8412345678905"); assertStripVisible()

        rule.onNodeWithTag("productos-row-8412345678905").performTouchInput { longClick() }
        rule.onNodeWithText("¿Eliminar este producto?").assertIsDisplayed()
        rule.onNodeWithText("Cancelar").assertIsDisplayed()
    }

    @Test
    fun ajustesKeepsTheMarginsInItsIdleAndConfirmingClearStates() {
        rule.onNodeWithContentDescription("Ajustes").performClick()
        rule.waitForIdle()
        assertInsideMargins("ajustes-sound", "ajustes-folder", "ajustes-version", "ajustes-clear"); assertStripVisible()
        rule.onNodeWithTag("ajustes-clear").performClick()
        rule.onNodeWithText("¿Vaciar la lista?").assertIsDisplayed()
        rule.onNodeWithText("Cancelar").assertIsDisplayed()
    }

    // ---- keyboard policy across the two screens that have fields
    private fun softKeyboardShown() = rule.activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true
    private fun settle() { rule.waitForIdle(); Thread.sleep(1500); rule.waitForIdle() }

    @Test
    fun withAPhysicalKeyboardNeitherEscanearNorAjustesOpensTheSoftKeyboard() {
        capture.value = CaptureState.Found("8412345678905", 1, false)
        settle()
        rule.onNodeWithTag("scan-cantidad").performClick(); settle()
        assertFalse("Escanear", softKeyboardShown())
        rule.onNodeWithContentDescription("Ajustes").performClick(); settle()
        rule.onNodeWithTag("ajustes-folder").performClick(); settle()
        assertFalse("Ajustes", softKeyboardShown())
    }

    @Test
    fun withoutAPhysicalKeyboardAjustesOpensIt() {
        physicalKeyboard.value = false
        rule.onNodeWithContentDescription("Ajustes").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("ajustes-folder").performClick()
        rule.waitUntil(5_000) { softKeyboardShown() }
    }
}
