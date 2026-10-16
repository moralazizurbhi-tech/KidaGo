package com.afede.kidago.ui.scan

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertContentDescriptionEquals
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

    private fun assertHeroAndClearance(lowest: Float) {
        val hero = b("scan-hero")
        near(229f, hero.width.value, "hero width"); near(102f, hero.height.value, "hero height")
        assertTrue("lowest element ends ${b("tab-bar").top.value - lowest} px above the tab bar", lowest <= b("tab-bar").top.value - 16f)
    }

    @Test
    fun idleHasTheFieldAt416x64AndFitsAboveTheTabBar() {
        show(CaptureState.Idle)
        near(416f, b("scan-input").width.value, "field width"); near(64f, b("scan-input").height.value, "field height")
        near(32f, b("scan-input").left.value, "left margin")
        near(32f, b("scan-input").top.value - b("scan-hero").bottom.value, "hero to field")
        assertHeroAndClearance(b("scan-input").bottom.value)
    }

    @Test
    fun searchingFitsAboveTheTabBar() {
        show(CaptureState.Searching)
        assertHeroAndClearance(bText("Buscando producto...").bottom.value)
    }

    @Test
    fun foundStacksWithTheReferenceGapsAndFits() {
        show(found)
        near(8f, b("scan-input").top.value - b("scan-hero").bottom.value, "hero to code")
        near(32f, b("scan-badge").top.value - b("scan-input").bottom.value, "code to badge")
        val heading = bText("Producto encontrado"); val sub = bText("Añadido a la lista")
        near(12f, heading.top.value - b("scan-badge").bottom.value, "badge to heading")
        near(12f, sub.top.value - heading.bottom.value, "heading to subtext")
        val label = bText("Cantidad")
        near(12f, label.top.value - sub.bottom.value, "subtext to action row")
        near(22f, label.height.value, "label height"); near(6f, b("scan-cantidad").top.value - label.bottom.value, "label to field")
        near(340f, b("scan-cantidad").width.value, "Cantidad width"); near(64f, b("scan-cantidad").height.value, "Cantidad height")
        val listo = b("scan-listo")
        near(64f, listo.width.value, "Listo width"); near(64f, listo.height.value, "Listo height")
        near(12f, listo.left.value - b("scan-cantidad").right.value, "Cantidad to Listo")
        near(b("scan-cantidad").bottom.value, listo.bottom.value, "Listo aligned to the field")
        near(right, listo.right.value, "Listo on the margin")
        assertHeroAndClearance(listo.bottom.value)
    }

    @Test
    fun notFoundHasListoAloneOnTheRightMargin() {
        show(notFound)
        val sub = bText("Comprueba el código o inténtalo de nuevo")
        near(12f, b("scan-listo").top.value - sub.bottom.value, "subtext to Listo")
        near(right, b("scan-listo").right.value, "Listo on the margin")
        near(64f, b("scan-listo").width.value, "Listo width")
        assertHeroAndClearance(b("scan-listo").bottom.value)
    }

    @Test
    fun errorGoesFromTheIllustrationToTheBadgeIn32AndFits() {
        show(CaptureState.Error)
        near(32f, b("scan-badge").top.value - b("scan-hero").bottom.value, "illustration to badge")
        near(right, b("scan-listo").right.value, "Listo on the margin")
        assertHeroAndClearance(b("scan-listo").bottom.value)
    }

    @Test
    fun listoIsNamedForAssistiveTechnology() {
        show(notFound)
        rule.onNodeWithTag("scan-listo").assertContentDescriptionEquals("Listo")
    }
}
