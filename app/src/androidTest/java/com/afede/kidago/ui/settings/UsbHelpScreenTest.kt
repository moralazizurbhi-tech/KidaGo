package com.afede.kidago.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.afede.kidago.data.UsbSnapshot
import com.afede.kidago.ui.theme.KidaGoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** FEAT-004 C14, C15: Conexión USB shows the live state, saves the log and returns with Volver. */
class UsbHelpScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun showsTheStateSavesTheLogAndGoesBack() {
        var back = false
        rule.setContent { KidaGoTheme { UsbHelpScreen(UsbSnapshot(charging = true), onSave = { "KidaGo-usb-1.txt" }, onBack = { back = true }) } }
        rule.onNodeWithTag("usb-status").assertTextEquals("Cargando, pero el PC no recibe datos")
        rule.onNodeWithTag("usb-save").performScrollTo().performClick()
        rule.onNodeWithTag("usb-save-result").assertTextEquals("Guardado en Descargas: KidaGo-usb-1.txt")
        rule.onNodeWithTag("usb-back").performScrollTo().performClick()
        assertTrue(back)
    }
}
