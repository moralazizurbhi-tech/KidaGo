package com.afede.kidago.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.afede.kidago.ui.theme.KidaGoButton
import com.afede.kidago.ui.theme.KidaGoNeutralButton
import com.afede.kidago.ui.theme.KidaGoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** TASK-054: at the largest system font (1.3x) the action row keeps its labels intact; it stacks when they cannot sit side by side. */
class ConfirmationPromptLargeFontTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun atTheLargestSystemFontTheLabelsStayIntactInTheDialogContentWidth() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                KidaGoTheme { Box(Modifier.width(352.dp)) { ActionRow { KidaGoNeutralButton("Cancelar", {}); KidaGoButton("Eliminar", {}) } } }
            }
        }
        val confirm = rule.onNodeWithText("Eliminar").getUnclippedBoundsInRoot()
        val decline = rule.onNodeWithText("Cancelar").getUnclippedBoundsInRoot()
        assertTrue("confirm not squeezed: ${confirm.right.value - confirm.left.value}", confirm.right.value - confirm.left.value >= 159f)
        assertTrue("decline not squeezed", decline.right.value - decline.left.value >= 159f)
        assertTrue("64 high", confirm.bottom.value - confirm.top.value <= 64.5f && decline.bottom.value - decline.top.value <= 64.5f)
        assertTrue("inside the content width", confirm.right.value <= 352f + 1f && decline.left.value >= -1f)
    }

    @Test
    fun aLongLabelForcesTheStackedLayoutWithConfirmOnTop() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                KidaGoTheme { Box(Modifier.width(352.dp)) { ActionRow { KidaGoNeutralButton("Cancelar", {}); KidaGoButton("Eliminar definitivamente", {}) } } }
            }
        }
        val confirm = rule.onNodeWithText("Eliminar definitivamente").getUnclippedBoundsInRoot()
        val decline = rule.onNodeWithText("Cancelar").getUnclippedBoundsInRoot()
        assertTrue("confirm above decline", confirm.bottom.value <= decline.top.value)
    }
}
