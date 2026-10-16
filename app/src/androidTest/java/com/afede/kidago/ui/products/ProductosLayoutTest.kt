package com.afede.kidago.ui.products

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.height
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.afede.kidago.data.AppDatabase
import com.afede.kidago.data.TodaysListEntryStore
import com.afede.kidago.ui.shell.AppShell
import com.afede.kidago.ui.theme.KidaGoTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** TASK-051: Productos at the PDA's scale (FEAT-003 UI Definition, Spacing and Layout). All values are design px. */
class ProductosLayoutTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var db: AppDatabase
    private lateinit var store: TodaysListEntryStore

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        store = TodaysListEntryStore(db)
    }

    @After
    fun tearDown() = db.close()

    private fun show() {
        rule.setContent {
            KidaGoTheme {
                AppShell(productsScreen = {
                    val vm = viewModel { ProductosListViewModel(store.entries, store.total, store::remove) }
                    val state by vm.state.collectAsState()
                    ProductosScreen(state, vm.confirmation, vm.removal::onLongPress, removalState = vm.removal.state.collectAsState().value)
                })
            }
        }
        rule.onNodeWithText("Productos").performClick() // the shell starts on Escanear
    }

    private fun scan(vararg codes: String) = runBlocking { codes.forEach { store.addOrAccumulate(it) } }
    private fun b(tag: String) = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
    private fun near(expected: Float, actual: Float, what: String) = assertEquals(what, expected, actual, 1f)

    @Test
    fun headerStartsAt32AndTheBadgeEndsAt32() {
        scan("8412345678905"); show(); rule.waitForIdle()
        val frame = b("tab-bar")
        near(32f, rule.onNodeWithText("Productos escaneados").getUnclippedBoundsInRoot().left.value, "title start")
        near(frame.right.value - 32f, b("productos-badge").right.value, "badge end")
    }

    @Test
    fun rowsShowTheCodeGrouped1_6_6WithA50pxRowAndTheStoredCodeIsUnchanged() {
        scan("8412345678905"); show(); rule.waitForIdle()
        rule.onNodeWithText("8 412345 678905").assertIsDisplayed()
        near(50f, b("productos-row-8412345678905").height.value - 1f, "row height (50 plus the hairline)")
        assertEquals("8412345678905", runBlocking { db.todaysListDao().entries() }.single().code)
    }

    @Test
    fun onlyTheListScrollsAndItFillsDownTo16pxAboveTheTabBar() {
        scan(*Array(40) { "84123456789%02d".format(it) }); show(); rule.waitForIdle()
        val headerTop = b("productos-badge").top.value
        near(b("tab-bar").top.value - 16f, b("productos-list").bottom.value + 16f, "list ends 16 px above the tab bar (inside the card's 16 px padding)")
        rule.onNodeWithTag("productos-list").performScrollToIndex(39)
        rule.waitForIdle()
        near(headerTop, b("productos-badge").top.value, "the header stays in place")
    }

    @Test
    fun emptyStateFitsWithoutScrolling() {
        show(); rule.waitForIdle()
        rule.onNodeWithTag("productos-empty").assertIsDisplayed()
        assertTrue(b("productos-empty").bottom.value <= b("tab-bar").top.value - 16f)
    }

    @Test
    fun theRemovalPromptFitsOnScreen() {
        scan("8412345678905"); show(); rule.waitForIdle()
        rule.onNodeWithTag("productos-row-8412345678905").performTouchInput { longClick() }
        rule.onNodeWithText("¿Eliminar este producto?").assertIsDisplayed()
        rule.onNodeWithText("Cancelar").assertIsDisplayed()
        rule.onNodeWithText("Eliminar").assertIsDisplayed()
    }
}
