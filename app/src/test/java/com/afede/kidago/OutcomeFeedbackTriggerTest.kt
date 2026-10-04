package com.afede.kidago

import com.afede.kidago.data.Settings
import com.afede.kidago.ui.scan.CaptureState
import com.afede.kidago.ui.scan.Cue
import com.afede.kidago.ui.scan.OutcomeCues
import com.afede.kidago.ui.scan.OutcomeFeedbackTrigger
import org.junit.Assert.assertEquals
import org.junit.Test

class OutcomeFeedbackTriggerTest {
    private val beeps = mutableListOf<Cue>()
    private val buzzes = mutableListOf<Cue>()
    private var settings = Settings(soundEnabled = true, vibrationEnabled = true, catalogFolder = "x")
    private val trigger = OutcomeFeedbackTrigger({ settings }, object : OutcomeCues {
        override fun beep(cue: Cue) { beeps += cue }
        override fun vibrate(cue: Cue) { buzzes += cue }
    })

    @Test
    fun eachOutcomeFiresBothCuesOnce() {
        trigger.onOutcome(CaptureState.Found("A", 1, false))
        trigger.onOutcome(CaptureState.NotFound("B"))
        trigger.onOutcome(CaptureState.Error)
        assertEquals(listOf(Cue.Found, Cue.NotFound, Cue.Error), beeps)
        assertEquals(listOf(Cue.Found, Cue.NotFound, Cue.Error), buzzes)
    }

    @Test
    fun soundAndVibrationAreIndependent() {
        settings = settings.copy(soundEnabled = false)
        trigger.onOutcome(CaptureState.Found("A", 1, false))
        assertEquals(emptyList<Cue>(), beeps)
        assertEquals(listOf(Cue.Found), buzzes)

        settings = settings.copy(soundEnabled = true, vibrationEnabled = false)
        trigger.onOutcome(CaptureState.Error)
        assertEquals(listOf(Cue.Error), beeps)
        assertEquals(listOf(Cue.Found), buzzes)

        settings = settings.copy(soundEnabled = false)
        trigger.onOutcome(CaptureState.NotFound("B"))
        assertEquals(1, beeps.size)
        assertEquals(1, buzzes.size)
    }

    @Test
    fun idleTypingAndSearchingNeverCue() {
        trigger.onOutcome(CaptureState.Idle)
        trigger.onOutcome(CaptureState.Typing)
        trigger.onOutcome(CaptureState.Searching)
        assertEquals(emptyList<Cue>(), beeps)
        assertEquals(emptyList<Cue>(), buzzes)
    }
}
