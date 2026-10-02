package com.afede.kidago.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileAccessGuardTest {
    private var granted = false
    private var settingsOpened = 0
    private val guard = FileAccessGuard({ granted }, { settingsOpened++ })

    @Test
    fun grantedReportsGrantedAndDoesNotOpenSettings() {
        granted = true
        assertTrue(guard.checkAtLaunchOrResume())
        assertTrue(guard.isGranted())
        assertEquals(0, settingsOpened)
    }

    @Test
    fun missingReportsNoAccessAndOpensSettingsAtEachCheck() {
        assertFalse(guard.checkAtLaunchOrResume())
        assertFalse(guard.checkAtLaunchOrResume()) // still missing at the next resume: asked again
        assertEquals(2, settingsOpened)
    }

    @Test
    fun stripTapOpensSettings() {
        guard.openSettings()
        assertEquals(1, settingsOpened)
    }

    @Test
    fun queryingStateAloneNeverOpensSettings() {
        guard.isGranted()
        assertEquals(0, settingsOpened)
    }

    @Test
    fun grantGivenLaterIsSeenAtTheNextCheck() {
        assertFalse(guard.checkAtLaunchOrResume())
        granted = true
        assertTrue(guard.checkAtLaunchOrResume())
        assertEquals(1, settingsOpened)
    }
}
