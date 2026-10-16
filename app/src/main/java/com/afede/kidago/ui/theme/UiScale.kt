package com.afede.kidago.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * UI scale (Project Architecture, Shared Technical Foundations): the design's measurements are the physical pixels
 * of the fixed 480x800 PDA screen, so one design px must render as one physical px whatever the system density or
 * density. This is the single place that conversion lives: inside [DesignPixels] 1.dp is 1 design px, and 1.sp is 1
 * design px times the system font size, capped at [MaxFontScale] so the fixed 480x800 layouts still fit.
 */
const val MaxFontScale = 1.3f

fun designDensity(systemFontScale: Float) = Density(density = 1f, fontScale = systemFontScale.coerceIn(1f, MaxFontScale))

@Composable
fun DesignPixels(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDensity provides designDensity(LocalDensity.current.fontScale), content = content)
}
