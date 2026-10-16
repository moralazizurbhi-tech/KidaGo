package com.afede.kidago.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** TASK-045: N design px renders as N physical px, whatever the system density or font scale. */
class UiScaleTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun designPxAt(system: Density): Pair<Float, Float> {
        var px = 0f
        var textPx = 0f
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides system) {
                KidaGoTheme {
                    with(LocalDensity.current) { px = 24.dp.toPx(); textPx = 16.sp.toPx() }
                }
            }
        }
        rule.waitForIdle()
        return px to textPx
    }

    @Test
    fun at240dpiADesignPxIsAPhysicalPx() = assertEquals(24f to 16f, designPxAt(Density(1.5f, 1f)))

    @Test
    fun theSameHoldsAtAnotherDensityAndFontScale() = assertEquals(24f to 16f, designPxAt(Density(2.75f, 1.3f)))
}
