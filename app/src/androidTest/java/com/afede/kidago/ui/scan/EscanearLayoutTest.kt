package com.afede.kidago.ui.scan

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.afede.kidago.data.SyncState
import com.afede.kidago.ui.catalog.CatalogStatusStrip
import com.afede.kidago.ui.shell.AppShell
import com.afede.kidago.ui.theme.KidaGoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** TASK-049: Escanear at the PDA's scale (FEAT-002 UI Definition, outcome stack; UX, Fit). All values are design px. */
class EscanearLayoutTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(state: CaptureState) = rule.setContent {
        KidaGoTheme {
            AppShell(
                headerContent = { CatalogStatusStrip(SyncState.UpToDate(null), {}) },
                scanScreen = { EscanearScreen(state, "", {}, {}, {}) },
            )
        }
    }

    private fun b(tag: String) = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
    private fun bText(text: String) = rule.onNodeWithText(text).getUnclippedBoundsInRoot()
    private val right get() = b("tab-bar").right.value - 32f
    private fun near(expected: Float, actual: Float, what: String) = assertEquals(what, expected, actual, 1f)

    private val found = CaptureState.Found("8412345678905", 1, false)
    private val notFound = CaptureState.NotFound("8412345678905")

    private fun assertClearance(lowest: Float) =
        assertTrue("lowest element ends ${b("tab-bar").top.value - lowest} px above the tab bar", lowest <= b("tab-bar").top.value - 16f)

    private fun assertHeroAndClearance(lowest: Float) {
        val hero = b("scan-hero")
        near(229f, hero.width.value, "hero width"); near(102f, hero.height.value, "hero height")
        assertClearance(lowest)
    }

    @Test
    fun idleHasTheFieldAt416x64AndFitsAboveTheTabBar() {
        show(CaptureState.Idle)
        near(416f, b("scan-input").width.value, "field width"); near(64f, b("scan-input").height.value, "field height")
        near(32f, b("scan-input").left.value, "left margin")
        near(32f, b("scan-input").top.value - b("scan-hero").bottom.value, "hero to field")
        rule.onNodeWithTag("screen-label").assertDoesNotExist() // the label belongs to results only
        assertHeroAndClearance(b("scan-input").bottom.value)
    }

    @Test
    fun searchingFitsAboveTheTabBar() {
        show(CaptureState.Searching)
        assertHeroAndClearance(bText("Buscando producto...").bottom.value)
    }

    /** Redesign 2026-11-21: no illustration; label on the right, card at 416, action row 12 px apart. */
    private fun assertResultStack() {
        rule.onNodeWithTag("scan-hero").assertDoesNotExist()
        near(right, b("screen-label").right.value, "label on the right margin")
        near(40f, b("screen-label").height.value, "label height")
        val card = rule.onNodeWithTag("result-code").getUnclippedBoundsInRoot()
        assertTrue("label above the card", b("screen-label").bottom.value < card.top.value)
    }

    @Test
    fun foundStacksLabelCardAndActionsAndFits() {
        show(found)
        assertResultStack()
        near(64f, b("scan-cantidad").height.value, "Cantidad height")
        val listo = b("scan-listo")
        near(64f, listo.height.value, "Listo height")
        near(12f, listo.left.value - b("scan-cantidad").right.value, "Cantidad to Listo")
        near(b("scan-cantidad").bottom.value, listo.bottom.value, "Listo aligned to the field")
        near(right, listo.right.value, "Listo on the margin")
        assertClearance(listo.bottom.value)
    }

    @Test
    fun notFoundHasListoAtFullWidth() {
        show(notFound)
        assertResultStack()
        near(416f, b("scan-listo").width.value, "Listo width")
        assertClearance(b("scan-listo").bottom.value)
    }

    @Test
    fun errorHasNoCodeRowAndFits() {
        show(CaptureState.Error)
        rule.onNodeWithTag("result-code").assertDoesNotExist()
        near(right, b("scan-listo").right.value, "Listo on the margin")
        assertClearance(b("scan-listo").bottom.value)
    }

    @Test
    fun listoIsLabelled() {
        show(notFound)
        rule.onNodeWithText("Listo").assertIsDisplayed()
    }
}
