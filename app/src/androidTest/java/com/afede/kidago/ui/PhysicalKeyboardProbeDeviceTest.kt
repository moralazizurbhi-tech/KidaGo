package com.afede.kidago.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** On the PDA the keypad exposes 0 to 9 (measured 2026-10-15), so the platform-backed probe answers present. */
@RunWith(AndroidJUnit4::class)
class PhysicalKeyboardProbeDeviceTest {
    @Test
    fun theYgfF7KeypadIsDetected() {
        assumeTrue(android.os.Build.MODEL.contains("YGF-F7"))
        val probe = PhysicalKeyboardProbe.forContext(InstrumentationRegistry.getInstrumentation().targetContext)
        assertTrue(probe.present.value)
    }
}
