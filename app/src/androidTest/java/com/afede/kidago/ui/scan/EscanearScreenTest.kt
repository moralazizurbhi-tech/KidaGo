package com.afede.kidago.ui.scan

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import com.afede.kidago.data.AppDatabase
import com.afede.kidago.data.LookupResult
import com.afede.kidago.data.SyncState
import com.afede.kidago.data.TodaysListEntryStore
import com.afede.kidago.scanner.FakeBarcodeScanner
import com.afede.kidago.scanner.ScanResult
import com.afede.kidago.ui.theme.KidaGoTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Escanear on the device: every state of the stateless screen, then the real ViewModel with a fake scanner and a real list. */
class EscanearScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val known = "1111111111111"
    private val unknown = "9999999999999"
    private lateinit var db: AppDatabase
    private lateinit var list: TodaysListEntryStore
    private val scanner = FakeBarcodeScanner()
    private val sync = MutableStateFlow<SyncState>(SyncState.UpToDate(null))

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        list = TodaysListEntryStore(db)
    }

    @After
    fun tearDown() {
        rule.activityRule.scenario.close() // stop the ViewModel's coroutines before the database goes away
        db.close()
    }

    private fun showViewModelScreen() = rule.setContent {
        KidaGoTheme {
            val vm = viewModel {
                EscanearViewModel(
                    scanner, sync,
                    { if (it == known) LookupResult.Found else LookupResult.NotFound },
                    list.entries, list::addOrAccumulate, list::applyQuantity,
                )
            }
            val state by vm.state.collectAsState()
            val typed by vm.typed.collectAsState()
            EscanearScreen(state, typed, vm::onTyped, vm::onSubmit, vm::onQuantitySubmit, onFinish = vm::onFinish)
        }
    }

    private fun showState(state: CaptureState, typed: String = "") = rule.setContent {
        KidaGoTheme { EscanearScreen(state, typed, {}, {}, {}) }
    }

    private fun quantity(code: String) = runBlocking { db.todaysListDao().find(code)?.quantity }
    private fun scan(code: String) { scanner.emit(ScanResult.Code(code)); rule.waitForIdle() }
    private fun typeAndSubmit(code: String) {
        rule.onNodeWithTag("scan-input").performTextInput(code)
        rule.onNodeWithTag("scan-input").performImeAction()
        rule.waitForIdle()
    }
    // The barcode field exposes what it shows: its digits grouped 1-6-6 (a plain field exposes what was typed).
    private fun grouped(digits: String) = digits.withIndex().joinToString("") { (i, c) -> (if (i == 1 || i == 7) " " else "") + c }
    private fun assertEditable(tag: String, shown: String) =
        rule.onNodeWithTag(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(shown)))
    private fun assertTyped(tag: String, digits: String) = assertEditable(tag, if (tag == "scan-input") grouped(digits) else digits)
    private fun assertEmpty(tag: String) = assertEditable(tag, "")

    private fun noOutcome() {
        for (tag in listOf("scan-outcome-found", "scan-outcome-notfound", "scan-outcome-error")) rule.onNodeWithTag(tag).assertDoesNotExist()
    }

    // ---- "Listo" (C20)

    @Test
    fun listoShowsOnlyInFoundNotFoundAndError() {
        val state = mutableStateOf<CaptureState>(CaptureState.Idle)
        rule.setContent { KidaGoTheme { EscanearScreen(state.value, "", {}, {}, {}) } }
        listOf(CaptureState.Idle, CaptureState.Typing, CaptureState.Searching).forEach {
            state.value = it; rule.waitForIdle()
            rule.onNodeWithTag("scan-listo").assertDoesNotExist()
        }
        listOf(CaptureState.Found(known, 1, false), CaptureState.NotFound(unknown), CaptureState.Error).forEach {
            state.value = it; rule.waitForIdle()
            rule.onNodeWithTag("scan-listo").assertIsDisplayed()
        }
    }

    @Test
    fun listoReturnsToIdleFromEachOutcomeAndLeavesTheListUnchanged() {
        showViewModelScreen()
        scan(unknown)
        rule.onNodeWithTag("scan-listo").performClick(); rule.waitForIdle()
        noOutcome(); rule.onNodeWithTag("scan-listo").assertDoesNotExist()
        scanner.emit(ScanResult.Failure); rule.waitForIdle()
        rule.onNodeWithTag("scan-listo").performClick(); rule.waitForIdle()
        noOutcome()
        scan(known)
        rule.onNodeWithTag("scan-listo").performClick(); rule.waitForIdle()
        noOutcome()
        assertEquals(1, quantity(known)) // the default +1, nothing else
    }

    @Test
    fun listoAppliesATypedCantidadThatWasNotSubmitted() {
        showViewModelScreen()
        scan(known)
        rule.onNodeWithTag("scan-cantidad").performTextInput("5")
        rule.onNodeWithTag("scan-listo").performClick(); rule.waitForIdle()
        assertEquals(5, quantity(known))
        noOutcome()
    }

    // ---- the six states, as drawn

    @Test
    fun idleShowsThePromptAndNoOutcome() {
        showState(CaptureState.Idle)
        rule.onNodeWithText("Introduce o escanea un código", useUnmergedTree = true).assertIsDisplayed()
        noOutcome()
    }

    @Test
    fun typingShowsTheTypedCharactersInTheSameField() {
        showState(CaptureState.Typing, typed = "123")
        assertTyped("scan-input", "123")
        noOutcome()
    }

    @Test
    fun searchingShowsTheSpinnerAndTheProgressPrompt() {
        showState(CaptureState.Searching)
        rule.onNodeWithTag("scan-spinner").assertIsDisplayed()
        rule.onNodeWithText("Buscando producto...").assertIsDisplayed()
        noOutcome()
    }

    @Test
    fun foundShowsHeadingSubtextAndACantidadFieldWithGhostOneAndNoStepper() {
        showState(CaptureState.Found(known, 1, wasExisting = false))
        rule.onNodeWithText("Producto encontrado").assertIsDisplayed()
        rule.onNodeWithText("Añadido a la lista").assertIsDisplayed()
        rule.onNodeWithText("Cantidad").assertIsDisplayed()
        rule.onNodeWithText("1", useUnmergedTree = true).assertIsDisplayed() // the ghost
        rule.onNodeWithTag("scan-rescan").assertDoesNotExist()
        rule.onNodeWithText("+").assertDoesNotExist()
        rule.onNodeWithText("−").assertDoesNotExist()
    }

    @Test
    fun rescanReplacesTheSubtextWithTheWarningAndTheAccumulatedTotal() {
        showState(CaptureState.Found(known, 4, wasExisting = true))
        rule.onNodeWithText("Producto encontrado").assertIsDisplayed()
        rule.onNodeWithText("Ya escaneado · Total acumulado: 4").assertIsDisplayed()
        rule.onNodeWithText("Añadido a la lista").assertDoesNotExist()
        rule.onNodeWithText("Cantidad").assertIsDisplayed() // same structure as a new item
    }

    @Test
    fun notFoundHasItsOwnHeadingAndNoCantidad() {
        showState(CaptureState.NotFound(unknown))
        rule.onNodeWithTag("scan-outcome-notfound").assertIsDisplayed()
        rule.onNodeWithText("Producto no encontrado").assertIsDisplayed()
        rule.onNodeWithText("Comprueba el código o inténtalo de nuevo").assertIsDisplayed()
        rule.onNodeWithText("Cantidad").assertDoesNotExist()
    }

    @Test
    fun errorHasItsOwnHeadingAndNoCantidad() {
        showState(CaptureState.Error)
        rule.onNodeWithTag("scan-outcome-error").assertIsDisplayed()
        rule.onNodeWithText("Error de lectura").assertIsDisplayed()
        rule.onNodeWithText("No se ha podido leer el código Inténtalo de nuevo").assertIsDisplayed()
        rule.onNodeWithText("Cantidad").assertDoesNotExist()
    }

    @Test
    fun stateChangesSwapTheOutcomeAfterTheTransition() {
        var state by mutableStateOf<CaptureState>(CaptureState.Idle)
        rule.setContent { KidaGoTheme { EscanearScreen(state, "", {}, {}, {}) } }
        state = CaptureState.NotFound(unknown); rule.waitForIdle()
        rule.onNodeWithTag("scan-outcome-notfound").assertIsDisplayed()
        state = CaptureState.Error; rule.waitForIdle()
        rule.onNodeWithTag("scan-outcome-error").assertIsDisplayed()
        rule.onNodeWithTag("scan-outcome-notfound").assertDoesNotExist()
    }


    // ---- the composition of the Figma frames

    @Test
    fun theHeroBarcodeIsInEveryState() {
        var state by mutableStateOf<CaptureState>(CaptureState.Idle)
        rule.setContent { KidaGoTheme { EscanearScreen(state, "", {}, {}, {}) } }
        for (s in listOf(CaptureState.Idle, CaptureState.Searching, CaptureState.Found(known, 1, false), CaptureState.NotFound(unknown), CaptureState.Error)) {
            state = s; rule.waitForIdle()
            rule.onNodeWithTag("scan-hero").assertIsDisplayed()
        }
    }

    @Test
    fun foundAndNotFoundEchoTheCodeGroupedInPlaceOfThePill() {
        showState(CaptureState.Found(known, 1, wasExisting = false))
        rule.onNodeWithText("1 111111 111111").assertIsDisplayed()
        rule.onNodeWithText("Introduce o escanea un código").assertDoesNotExist()
    }

    @Test
    fun errorShowsNoCodeAndNoPill() {
        showState(CaptureState.Error)
        rule.onNodeWithText("Introduce o escanea un código").assertDoesNotExist()
        rule.onNodeWithTag("scan-clear").assertDoesNotExist()
    }

    @Test
    fun theClearButtonAppearsWithTextAndEmptiesTheField() {
        showViewModelScreen()
        rule.onNodeWithTag("scan-clear").assertDoesNotExist()
        rule.onNodeWithTag("scan-input").performTextInput("123")
        rule.onNodeWithTag("scan-clear").assertIsDisplayed().performClick()
        rule.waitForIdle()
        assertEmpty("scan-input")
        rule.onNodeWithTag("scan-clear").assertDoesNotExist()
    }

    @Test
    fun aHardwareScanEchoesItsCodeEvenThoughNothingWasTyped() {
        showViewModelScreen()
        scan(unknown)
        rule.onNodeWithText("9 999999 999999").assertIsDisplayed() // nothing was typed; the scanned code is shown in its place
    }

    @Test
    fun typedDigitsAreShownGroupedWhileSearching() {
        showState(CaptureState.Searching, typed = "8601153155321")
        rule.onNodeWithText("8 601153 155321").assertIsDisplayed()
        rule.onNodeWithTag("scan-spinner").assertIsDisplayed()
    }

    // ---- with the real ViewModel

    @Test
    fun manualEntryOnlySubmitsAtExactly13DigitsThenAddsAndFreesTheField() {
        showViewModelScreen()
        typeAndSubmit("111")
        noOutcome()
        assertTyped("scan-input", "111")
        rule.onNodeWithTag("scan-input").performTextInput("1111111111111999") // only digits up to 13 are kept
        assertTyped("scan-input", known)
        rule.onNodeWithTag("scan-input").performImeAction()
        rule.waitUntil(3_000) { quantity(known) == 1 }
        rule.onNodeWithText("Producto encontrado").assertIsDisplayed()
        assertEditable("scan-input", grouped(known)) // the added code is echoed in the field's place
        rule.onNodeWithTag("scan-input").performTextInput("5") // and the next one starts from nothing, not from the echo
        assertTyped("scan-input", "5")
    }

    @Test
    fun anUnknownManualCodeIsNotFoundAndStaysInTheFieldToBeChecked() {
        showViewModelScreen()
        typeAndSubmit(unknown)
        rule.onNodeWithText("Producto no encontrado").assertIsDisplayed()
        assertTyped("scan-input", unknown)
        assertEquals(null, quantity(unknown))
    }

    @Test
    fun hardwareScanNeedsNoSubmitAndARescanWarnsWithTheTotal() {
        showViewModelScreen()
        scan(known)
        rule.waitUntil(3_000) { quantity(known) == 1 }
        rule.onNodeWithText("Añadido a la lista").assertIsDisplayed()
        scan(known)
        rule.waitUntil(3_000) { quantity(known) == 2 }
        rule.onNodeWithText("Ya escaneado · Total acumulado: 2").assertIsDisplayed()
    }

    @Test
    fun aNewCaptureReplacesTheShownOutcomeAndAReadFailureIsAnError() {
        showViewModelScreen()
        scan(known)
        rule.waitUntil(3_000) { quantity(known) == 1 }
        scan(unknown)
        rule.onNodeWithText("Producto no encontrado").assertIsDisplayed()
        rule.onNodeWithTag("scan-outcome-found").assertDoesNotExist()
        scanner.emit(ScanResult.Failure); rule.waitForIdle()
        rule.onNodeWithText("Error de lectura").assertIsDisplayed()
        rule.onNodeWithTag("scan-outcome-notfound").assertDoesNotExist()
    }

    @Test
    fun cantidadSetsThisScansQuantityWithAtMostTwoDigits() {
        showViewModelScreen()
        scan(known)
        rule.waitUntil(3_000) { quantity(known) == 1 }
        rule.onNodeWithTag("scan-cantidad").performTextInput("123") // two digits at most
        assertTyped("scan-cantidad", "12")
        rule.onNodeWithTag("scan-cantidad").performImeAction()
        rule.waitUntil(3_000) { quantity(known) == 12 }
    }

    @Test
    fun cantidadRejectsZeroAndKeepsTheDefault() {
        showViewModelScreen()
        scan(known)
        rule.waitUntil(3_000) { quantity(known) == 1 }
        rule.onNodeWithTag("scan-cantidad").performTextInput("0")
        rule.onNodeWithTag("scan-cantidad").performImeAction()
        rule.waitForIdle()
        assertEmpty("scan-cantidad")
        assertEquals(1, quantity(known))
    }

    @Test
    fun captureIsIgnoredWhileTheCatalogSyncs() {
        showViewModelScreen()
        sync.value = SyncState.Syncing(0.3f)
        rule.waitForIdle()
        scan(known)
        typeAndSubmit(known)
        noOutcome()
        assertEquals(null, quantity(known))
    }
}
