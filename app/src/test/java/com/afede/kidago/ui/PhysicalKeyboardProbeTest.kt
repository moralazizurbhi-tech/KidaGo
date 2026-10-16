package com.afede.kidago.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalKeyboardProbeTest {
    @Test
    fun aDeviceWithDigitKeysGivesPresent() = assertTrue(PhysicalKeyboardProbe { listOf(false, true) }.present.value)

    @Test
    fun onlyVolumePowerOrTouchInputsGiveAbsent() = assertFalse(PhysicalKeyboardProbe { listOf(false, false) }.present.value)

    @Test
    fun whenThePlatformCannotAnswerItIsPresent() {
        assertTrue(PhysicalKeyboardProbe { null }.present.value)
        assertTrue(PhysicalKeyboardProbe { emptyList() }.present.value)
        assertTrue(PhysicalKeyboardProbe { error("no answer") }.present.value)
    }

    @Test
    fun refreshReevaluatesAfterAnInputDeviceChange() {
        var devices = listOf(false)
        val probe = PhysicalKeyboardProbe { devices }
        assertFalse(probe.present.value)
        devices = listOf(false, true) // a keyboard is plugged in
        probe.refresh()
        assertTrue(probe.present.value)
    }
}
