package com.afede.kidago.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.afede.kidago.ui.theme.KidaGoTheme
import org.junit.Assert.assertEquals
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
}
