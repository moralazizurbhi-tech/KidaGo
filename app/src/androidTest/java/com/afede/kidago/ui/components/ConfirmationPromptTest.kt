package com.afede.kidago.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import com.afede.kidago.ui.theme.KidaGoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ConfirmationPromptTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val controller = ConfirmationController()
    private var confirmed = 0
    private var declined = 0

    @Before
    fun setUp() {
        rule.setContent { KidaGoTheme { ConfirmationHost(controller) } }
    }

    private fun ask(title: String = "¿Eliminar este producto?") = rule.runOnUiThread {
        controller.request(ConfirmationRequest(title, "Eliminar", onConfirm = { confirmed++ }, onDecline = { declined++ }))
    }

    @Test
    fun showsTextAndConfirmFiresOnce() {
        ask()
        rule.onNodeWithText("¿Eliminar este producto?").assertIsDisplayed()
        rule.onNodeWithText("Eliminar").performClick()
        rule.onNodeWithText("¿Eliminar este producto?").assertDoesNotExist()
        assertEquals(1, confirmed)
        assertEquals(0, declined)
    }

    @Test
    fun declineFiresOnceAndClosesThePrompt() {
        ask()
        rule.onNodeWithText("Cancelar").performClick()
        rule.onNodeWithText("¿Eliminar este producto?").assertDoesNotExist()
        assertEquals(1, declined)
        assertEquals(0, confirmed)
    }

    @Test
    fun secondRequestWhilePendingIsIgnored() {
        ask("primero")
        ask("segundo")
        rule.onNodeWithText("primero").assertIsDisplayed()
        rule.onNodeWithText("segundo").assertDoesNotExist()
    }

    // ---- Action row (Project UX Specification v8): TASK-054
    private fun show(confirmLabel: String, declineLabel: String = "Cancelar") {
        rule.runOnUiThread {
            controller.request(ConfirmationRequest("¿Seguro?", confirmLabel, declineLabel, onConfirm = {}, onDecline = {}))
        }
    }

    private fun b(text: String): DpRect = rule.onNodeWithText(text).getUnclippedBoundsInRoot().let { DpRect(it.left, it.top, it.right, it.bottom) }
    private fun near(expected: Float, actual: Float, what: String) = assertEquals(what, expected, actual, 1f)

    @Test
    fun vaciarAndCancelarSitSideBySideDeclineLeftAtLeast160WideAnd64High() {
        show(confirmLabel = "Vaciar")
        val decline = b("Cancelar")
        val confirm = b("Vaciar")
        assertTrue("decline is left of confirm: decline=$decline confirm=$confirm", decline.right.value <= confirm.left.value)
        near(decline.top.value, confirm.top.value, "same row")
        near(16f, confirm.left.value - decline.right.value, "gap between the buttons")
        near(64f, confirm.bottom.value - confirm.top.value, "confirm height")
        assertTrue("confirm at least 160 wide: ${confirm.right.value - confirm.left.value}", confirm.right.value - confirm.left.value >= 159f)
        assertTrue("decline at least 160 wide", decline.right.value - decline.left.value >= 159f)
    }

    @Test
    fun theLabelStaysOnOneLine() {
        show(confirmLabel = "Vaciar")
        val label = rule.onNodeWithText("Vaciar", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("label height ${label.bottom.value - label.top.value}", label.bottom.value - label.top.value < 40f)
    }

    @Test
    fun whenTheyDoNotFitTheyStackAtFullWidthConfirmOnTop() {
        show(confirmLabel = "Eliminar definitivamente todo")
        val decline = b("Cancelar")
        val confirm = b("Eliminar definitivamente todo")
        assertTrue("confirm above decline", confirm.bottom.value <= decline.top.value)
        near(confirm.left.value, decline.left.value, "same left")
        near(confirm.right.value, decline.right.value, "same width")
        near(16f, decline.top.value - confirm.bottom.value, "gap between the rows")
        near(64f, decline.bottom.value - decline.top.value, "decline height")
    }
}
