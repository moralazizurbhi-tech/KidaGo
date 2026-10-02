package com.afede.kidago.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tokens must match Project UX Specification, UX Analysis (Colour). */
class ThemeTokensTest {
    @Test
    fun paletteMatchesUxSpecification() {
        assertEquals(Color(0xFFF7F6F3), KidaGoColors.Background)
        assertEquals(Color(0xFF1D1D1D), KidaGoColors.Text)
        assertEquals(Color(0xFFE3001B), KidaGoColors.Accent)
        assertEquals(Color(0xFF1B882B), KidaGoColors.SuccessText)
        assertEquals(Color(0xFF06752D), KidaGoColors.SuccessIcon)
        assertEquals(Color(0xFFFFFFFF), KidaGoColors.Surface)
        assertEquals(Color(0xFFD9D9D9), KidaGoColors.Border)
    }

    @Test
    fun colorSchemeUsesThePalette() {
        assertEquals(KidaGoColors.Background, KidaGoColorScheme.background)
        assertEquals(KidaGoColors.Accent, KidaGoColorScheme.primary)
        assertEquals(KidaGoColors.Accent, KidaGoColorScheme.error)
    }

    @Test
    fun manropeForUiTextAndFredokaOnlyForWordmark() {
        assertEquals(Manrope, KidaGoTypography.bodyLarge.fontFamily)
        assertEquals(Manrope, KidaGoTypography.headlineMedium.fontFamily)
        assertEquals(Fredoka, KidaGoWordmark.fontFamily)
    }
}
