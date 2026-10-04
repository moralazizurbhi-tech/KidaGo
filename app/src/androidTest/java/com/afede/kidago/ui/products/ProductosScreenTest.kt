package com.afede.kidago.ui.products

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.down
import androidx.compose.ui.test.up
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.afede.kidago.data.AppDatabase
import com.afede.kidago.data.ArchiveReason
import com.afede.kidago.data.TodaysListEntryStore
import com.afede.kidago.ui.theme.KidaGoTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Productos against the real store and a real ViewModel, on the device. */
class ProductosScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var db: AppDatabase
    private lateinit var store: TodaysListEntryStore

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        store = TodaysListEntryStore(db)
        rule.setContent {
            KidaGoTheme {
                val vm = viewModel { ProductosListViewModel(store.entries, store.total, store::remove) }
                val state by vm.state.collectAsState()
                ProductosScreen(state, vm.confirmation, vm.removal::onLongPress, removalState = vm.removal.state.collectAsState().value)
            }
        }
    }

    @After
    fun tearDown() = db.close()

    private fun scan(vararg codes: String) = runBlocking { codes.forEach { store.addOrAccumulate(it) } }
    private fun top(code: String) = rule.onNodeWithTag("productos-row-$code").getUnclippedBoundsInRoot().top

    @Test
    fun emptyListShowsEmptyStateAndZeroCount() {
        rule.onNodeWithTag("productos-empty").assertIsDisplayed()
        rule.onNodeWithText("No hay productos escaneados").assertIsDisplayed()
        rule.onNodeWithText("Comienza a escanear para añadir productos a la lista").assertIsDisplayed()
        rule.onNodeWithTag("productos-count").assertTextEquals("0")
    }

    @Test
    fun everyEntryShownOnceWithCodeAndQuantity_newestFirst_countIsTheTotal() {
        scan("1111111111111", "2222222222222", "2222222222222", "3333333333333", "2222222222222")
        rule.waitForIdle()
        rule.onNodeWithTag("productos-empty").assertDoesNotExist()
        rule.onNodeWithTag("productos-count").assertTextEquals("5") // 1 + 3 + 1
        rule.onNodeWithTag("productos-row-2222222222222").assertIsDisplayed()
        rule.onNodeWithText("3").assertIsDisplayed() // the accumulated quantity, on one line
        assertTrue(top("3333333333333") < top("2222222222222") && top("2222222222222") < top("1111111111111"))
    }

    @Test
    fun rescanKeepsPositionAndUpdatesCountImmediately() {
        scan("1111111111111", "2222222222222", "3333333333333")
        rule.waitForIdle()
        val before = top("2222222222222")
        scan("2222222222222")
        rule.waitForIdle()
        assertEquals(before, top("2222222222222"))
        rule.onNodeWithTag("productos-count").assertTextEquals("4")
        assertTrue(top("3333333333333") < top("2222222222222"))
    }

    @Test
    fun removalLeavesOthersInOrderAndDropsTheCount() {
        scan("1111111111111", "2222222222222", "2222222222222", "3333333333333")
        rule.waitForIdle()
        runBlocking { store.remove("2222222222222") }
        rule.waitForIdle()
        rule.onNodeWithTag("productos-row-2222222222222").assertDoesNotExist()
        rule.onNodeWithTag("productos-count").assertTextEquals("2")
        assertTrue(top("3333333333333") < top("1111111111111"))
    }

    @Test
    fun resetShowsEmptyStateWithZeroCount() {
        scan("1111111111111", "2222222222222")
        rule.waitForIdle()
        runBlocking { store.archiveAndClear(ArchiveReason.MANUAL) }
        rule.waitForIdle()
        rule.onNodeWithTag("productos-empty").assertIsDisplayed()
        rule.onNodeWithTag("productos-count").assertTextEquals("0")
    }

    @Test
    fun tapOnARowChangesNothingAndShowsNoControl() {
        scan("1111111111111", "2222222222222")
        rule.waitForIdle()
        rule.onNodeWithTag("productos-row-1111111111111").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("¿Eliminar este producto?").assertDoesNotExist()
        rule.onNodeWithTag("productos-count").assertTextEquals("2")
    }

    private fun longPress(code: String) = rule.onNodeWithTag("productos-row-$code").performTouchInput { longClick() }

    @Test
    fun longPressAsksFirstAndRemovesNothingYet() {
        scan("1111111111111", "2222222222222")
        rule.waitForIdle()
        longPress("1111111111111")
        rule.onNodeWithText("¿Eliminar este producto?").assertIsDisplayed()
        rule.onNodeWithText("Eliminar").assertIsDisplayed()
        rule.onNodeWithText("Cancelar").assertIsDisplayed()
        assertEquals(2, runBlocking { db.todaysListDao().entries() }.size) // nothing written before confirmation
        rule.onNodeWithTag("productos-count").assertTextEquals("2")
    }

    @Test
    fun confirmRemovesTheEntryDropsTheCountAndWritesNoHistory() {
        scan("1111111111111", "2222222222222", "2222222222222", "3333333333333")
        rule.waitForIdle()
        longPress("2222222222222")
        rule.onNodeWithText("Eliminar").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("¿Eliminar este producto?").assertDoesNotExist()
        rule.onNodeWithTag("productos-row-2222222222222").assertDoesNotExist()
        rule.onNodeWithTag("productos-count").assertTextEquals("2")
        assertTrue(top("3333333333333") < top("1111111111111"))
        assertTrue(runBlocking { store.history() }.isEmpty())
    }

    @Test
    fun confirmingTheOnlyEntryShowsTheEmptyState() {
        scan("1111111111111")
        rule.waitForIdle()
        longPress("1111111111111")
        rule.onNodeWithText("Eliminar").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("productos-empty").assertIsDisplayed()
        rule.onNodeWithTag("productos-count").assertTextEquals("0")
    }

    @Test
    fun declineLeavesTheEntryUnchanged() {
        scan("1111111111111", "1111111111111", "2222222222222")
        rule.waitForIdle()
        longPress("1111111111111")
        rule.onNodeWithText("Cancelar").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("¿Eliminar este producto?").assertDoesNotExist()
        rule.onNodeWithTag("productos-row-1111111111111").assertIsDisplayed()
        rule.onNodeWithTag("productos-count").assertTextEquals("3")
        assertEquals(2, runBlocking { db.todaysListDao().find("1111111111111") }!!.quantity)
    }

    @Test
    fun removedEntryIsAbsentFromALaterHistoryRecord() {
        scan("1111111111111", "2222222222222")
        rule.waitForIdle()
        longPress("1111111111111")
        rule.onNodeWithText("Eliminar").performClick()
        rule.waitForIdle()
        runBlocking { store.archiveAndClear(ArchiveReason.MANUAL) }
        val record = runBlocking { store.history() }.single()
        assertEquals(listOf("2222222222222"), runBlocking { store.historyEntries(record.id) }.map { it.code })
    }

    // ---- pressed-row feedback (C13): compared against an untouched row, so a modal scrim over both does not matter

    private fun green(code: String) = rule.onNodeWithTag("productos-row-$code").captureToImage().toPixelMap()[2, 4].green
    private fun assertTinted(code: String, other: String) = assertTrue(green(code) < green(other))
    private fun assertNotTinted(code: String, other: String) = assertEquals(green(code), green(other), 0.001f)

    @Test
    fun heldRowShowsTheTintAndReleaseClearsIt() {
        scan("1111111111111", "2222222222222")
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("productos-row-1111111111111").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(60)
        assertNotTinted("1111111111111", "2222222222222") // not held long enough yet
        rule.mainClock.advanceTimeBy(260) // past the 150 ms hold and the fade, before the long-press
        assertTinted("1111111111111", "2222222222222")
        assertNotTinted("2222222222222", "2222222222222")
        rule.onNodeWithTag("productos-row-1111111111111").performTouchInput { up() }
        rule.mainClock.advanceTimeBy(400)
        assertNotTinted("1111111111111", "2222222222222")
        rule.onNodeWithText("¿Eliminar este producto?").assertDoesNotExist()
        rule.mainClock.autoAdvance = true
    }

    @Test
    fun quickTapShowsNoTint() {
        scan("1111111111111", "2222222222222")
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("productos-row-1111111111111").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(80)
        rule.onNodeWithTag("productos-row-1111111111111").performTouchInput { up() }
        rule.mainClock.advanceTimeBy(300)
        assertNotTinted("1111111111111", "2222222222222")
        rule.mainClock.autoAdvance = true
    }

    @Test
    fun tintStaysWhileThePromptIsOpenAndIsGoneAfterDecline() {
        scan("1111111111111", "2222222222222")
        rule.waitForIdle()
        longPress("1111111111111")
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithText("¿Eliminar este producto?").assertIsDisplayed()
        assertTinted("1111111111111", "2222222222222")
        rule.onNodeWithText("Cancelar").performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(300)
        assertNotTinted("1111111111111", "2222222222222")
        assertEquals(2, runBlocking { db.todaysListDao().entries() }.size) // no data changed
    }
}
