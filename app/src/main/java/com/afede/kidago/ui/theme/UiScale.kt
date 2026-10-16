package com.afede.kidago.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * UI scale (Project Architecture, Shared Technical Foundations): the design's measurements are the physical pixels
 * of the fixed 480x800 PDA screen, so one design px must render as one physical px whatever the system density or
 * font scale. This is the single place that conversion lives: inside [DesignPixels] 1.dp and 1.sp are 1 design px.
 */
val DesignDensity = Density(density = 1f, fontScale = 1f)

@Composable
fun DesignPixels(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDensity provides DesignDensity, content = content)
}
