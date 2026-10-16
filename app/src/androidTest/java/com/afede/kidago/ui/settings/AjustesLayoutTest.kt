package com.afede.kidago.ui.settings

import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.afede.kidago.ui.components.ConfirmationController
import com.afede.kidago.ui.components.ConfirmationRequest
import com.afede.kidago.ui.shell.AppShell
import com.afede.kidago.ui.theme.KidaGoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** TASK-052: Ajustes at the PDA's scale and the keyboard policy for the folder field (FEAT-004 UI Definition, Fit). Design px. */
class AjustesLayoutTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val confirmation = ConfirmationController()
    private val state = AjustesState(soundEnabled = true, vibrationEnabled = true, catalogFolder = "/storage/emulated/0/afede/kidago", version = "0.1.0")

    private fun show(physicalKeyboard: Boolean = true, onClear: () -> Unit = {}) {
        rule.setContent {
            KidaGoTheme {
                AppShell(settingsScreen = { AjustesScreen(state, {}, {}, { true }, confirmation, onClear, physicalKeyboard = physicalKeyboard) })
            }
        }
        rule.onNodeWithContentDescription("Ajustes").performClick() // the shell starts on Escanear
        rule.waitForIdle()
    }

    private fun b(tag: String) = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
    private fun near(expected: Float, actual: Float, what: String) = assertEquals(what, expected, actual, 1f)

    @Test
    fun theCardHas16pxTopPaddingAnd8pxBetweenRowsAndKeepsThe32pxSideMargins() {
        show()
        val card = b("ajustes-card")
        near(16f, rule.onNodeWithText("Sonido").getUnclippedBoundsInRoot().top.value - card.top.value, "top padding")
        near(32f, b("ajustes-sound").right.value.let { card.right.value - it }, "side margin (switch to card edge)")
        // Sonido's subtext, 8 px, a 1 px divider, 8 px, then Vibrar.
        val gap = rule.onNodeWithText("Vibrar").getUnclippedBoundsInRoot().top.value -
            rule.onNodeWithText("Utilizar alertas de sonido al escanear").getUnclippedBoundsInRoot().bottom.value
        near(17f, gap, "gap between rows")
    }

    @Test
    fun allFiveRowsAreVisibleWithAtLeast16pxAboveTheTabBar() {
        show()
        listOf("ajustes-sound", "ajustes-vibration", "ajustes-folder", "ajustes-version", "ajustes-clear").forEach { rule.onNodeWithTag(it).assertIsDisplayed() }
        val bottom = b("ajustes-clear").bottom.value
        assertTrue("last row ends ${b("tab-bar").top.value - bottom} px above the tab bar", bottom <= b("tab-bar").top.value - 16f)
    }

    @Test
    fun theConfirmingClearStateFits() {
        show(onClear = { confirmation.request(ConfirmationRequest("¿Vaciar la lista?", "Vaciar", onConfirm = {})) })
        rule.onNodeWithTag("ajustes-clear").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Cancelar").assertIsDisplayed()
    }

    // ---- keyboard policy for "Ruta del catálogo"
    private fun softKeyboardShown() = rule.activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true
    private fun settle() { rule.waitForIdle(); Thread.sleep(1500); rule.waitForIdle() }

    @Test
    fun withAPhysicalKeyboardTappingTheFolderFieldOpensNoSoftKeyboard() {
        show(physicalKeyboard = true)
        rule.onNodeWithTag("ajustes-folder").performClick()
        settle()
        assertFalse("soft keyboard must stay closed", softKeyboardShown())
    }

    @Test
    fun withoutAPhysicalKeyboardTappingTheFolderFieldOpensTheSoftKeyboard() {
        show(physicalKeyboard = false)
        rule.onNodeWithTag("ajustes-folder").performClick()
        rule.waitUntil(5_000) { softKeyboardShown() }
    }
}
