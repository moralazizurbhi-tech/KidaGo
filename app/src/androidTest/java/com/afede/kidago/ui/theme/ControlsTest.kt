package com.afede.kidago.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ControlsTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun buttonsRenderAndClick() {
        var confirmed = 0
        var declined = 0
        rule.setContent {
            KidaGoTheme {
                Column {
                    KidaGoButton("Confirmar", { confirmed++ })
                    KidaGoNeutralButton("Cancelar", { declined++ })
                }
            }
        }
        rule.onNodeWithText("Confirmar").assertIsDisplayed().performClick()
        rule.onNodeWithText("Cancelar").assertIsDisplayed().performClick()
        assertEquals(1, confirmed)
        assertEquals(1, declined)
    }
}
