package com.afede.kidago

import com.afede.kidago.ui.components.ConfirmationController
import com.afede.kidago.ui.settings.ClearState
import com.afede.kidago.ui.settings.ListClearCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListClearCoordinatorTest {
    private var clears = 0
    private val confirmation = ConfirmationController()
    private val coordinator = ListClearCoordinator(confirmation, CoroutineScope(Dispatchers.Unconfined)) { clears++ }

    @Test
    fun triggerOnlyAsksAndWritesNothing() {
        coordinator.onClearTapped()
        assertEquals(ClearState.ConfirmingClear, coordinator.state.value)
        assertEquals("¿Vaciar la lista actual?", confirmation.current!!.title)
        assertEquals(0, clears)
    }

    @Test
    fun confirmClearsOnceAndReturnsToIdle() {
        coordinator.onClearTapped()
        confirmation.confirm()
        confirmation.confirm() // a double tap must not clear twice
        assertEquals(1, clears)
        assertEquals(ClearState.Idle, coordinator.state.value)
        assertNull(confirmation.current)
    }

    @Test
    fun declineWritesNothingAndReturnsToIdle() {
        coordinator.onClearTapped()
        confirmation.decline()
        assertEquals(0, clears)
        assertEquals(ClearState.Idle, coordinator.state.value)
    }

    @Test
    fun aSecondTriggerWhilePendingIsIgnored() {
        coordinator.onClearTapped()
        coordinator.onClearTapped()
        confirmation.confirm()
        assertEquals(1, clears)
    }
}
