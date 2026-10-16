package com.afede.kidago.ui.scan

import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.afede.kidago.ui.theme.KidaGoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/** TASK-050: the keyboard policy (FEAT-002 UX, Keyboard and focus). */
class EscanearKeyboardTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val typed = mutableStateOf("")
    private var submits = 0

    private fun show(state: CaptureState, physicalKeyboard: Boolean) = rule.setContent {
        KidaGoTheme {
            EscanearScreen(state, typed.value, { typed.value = it.filter(Char::isDigit) }, { submits++ }, {}, physicalKeyboard = physicalKeyboard)
        }
    }

    private fun softKeyboardShown() = rule.activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true
    private fun settle() { rule.waitForIdle(); Thread.sleep(1500); rule.waitForIdle() }

    private val found = CaptureState.Found("8412345678905", 1, false)

    @Test
    fun withAPhysicalKeyboardEnteringEscanearKeepsTheFocusAndOpensNoSoftKeyboard() {
        show(CaptureState.Idle, physicalKeyboard = true)
        settle()
        rule.onNodeWithTag("scan-input").assertIsFocused()
        assertFalse("soft keyboard must stay closed", softKeyboardShown())
    }

    @Test
    fun withAPhysicalKeyboardTappingCantidadOpensNoSoftKeyboardEither() {
        show(found, physicalKeyboard = true)
        settle()
        rule.onNodeWithTag("scan-cantidad").performClick()
        settle()
        rule.onNodeWithTag("scan-cantidad").assertIsFocused()
        assertFalse("soft keyboard must stay closed", softKeyboardShown())
    }

    @Test
    fun withAPhysicalKeyboardKeysAreTypedAndEnterSubmits() {
        show(CaptureState.Idle, physicalKeyboard = true)
        settle()
        rule.onNodeWithTag("scan-input").performKeyInput { pressKey(Key.Five); pressKey(Key.Two) }
        rule.waitForIdle()
        assertEquals("52", typed.value)
        rule.onNodeWithTag("scan-input").performKeyInput { pressKey(Key.Enter) }
        rule.waitForIdle()
        assertEquals(1, submits)
    }

    @Test
    fun withoutAPhysicalKeyboardTheSoftKeyboardOpensNormally() {
        show(CaptureState.Idle, physicalKeyboard = false)
        rule.waitUntil(5_000) { softKeyboardShown() }
    }
}
