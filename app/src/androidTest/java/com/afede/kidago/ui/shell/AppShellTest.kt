package com.afede.kidago.ui.shell

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.afede.kidago.ui.theme.KidaGoTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AppShellTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        rule.setContent { KidaGoTheme { AppShell(productsBadge = 7) } }
    }

    private fun back() = rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
    private fun tab(label: String) = rule.onNodeWithText(label) // tab labels; screens use the "screen-*" tags
    private fun gear() = rule.onNodeWithContentDescription("Ajustes")

    @Test
    fun startsOnEscanearAndTabsSwitchBothWays() {
        rule.onNodeWithTag("screen-scan").assertIsDisplayed()
        tab("Productos").performClick()
        rule.onNodeWithTag("screen-products").assertIsDisplayed()
        tab("Escanear").performClick()
        rule.onNodeWithTag("screen-scan").assertIsDisplayed()
    }

    @Test
    fun tabBarShowsLiveBadgeOnProductos() {
        rule.onNodeWithTag("tab-badge", useUnmergedTree = true).assertIsDisplayed().assertTextEquals("7")
    }

    @Test
    fun gearOpensAjustesFromEscanearAndBackReturnsThere() {
        gear().performClick()
        rule.onNodeWithTag("screen-settings").assertIsDisplayed()
        back()
        rule.onNodeWithTag("screen-scan").assertIsDisplayed()
    }

    @Test
    fun gearOpensAjustesFromProductosAndBackReturnsThere() {
        tab("Productos").performClick()
        gear().performClick()
        rule.onNodeWithTag("screen-settings").assertIsDisplayed()
        back()
        rule.onNodeWithTag("screen-products").assertIsDisplayed()
    }

    @Test
    fun repeatedGearTapsDoNotStackAjustes() {
        gear().performClick()
        gear().performClick()
        back()
        rule.onNodeWithTag("screen-scan").assertIsDisplayed()
    }

    @Test
    fun tabBarStaysAvailableOnAjustesAndLeavesIt() {
        gear().performClick()
        tab("Productos").assertIsDisplayed().performClick()
        rule.onNodeWithTag("screen-products").assertIsDisplayed()
    }
}
