package com.afede.kidago

import com.afede.kidago.ui.components.ConfirmationController
import com.afede.kidago.ui.products.EntryRemovalCoordinator
import com.afede.kidago.ui.products.RemovalState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EntryRemovalCoordinatorTest {
    private val removed = mutableListOf<String>()
    private val confirmation = ConfirmationController()
    private val coordinator = EntryRemovalCoordinator(confirmation, CoroutineScope(Dispatchers.Unconfined)) { removed += it }

    @Test
    fun longPressOnlyAsksAndWritesNothing() {
        coordinator.onLongPress("A")
        assertEquals(RemovalState.ConfirmingRemoval("A"), coordinator.state.value)
        assertEquals("¿Eliminar este producto?", confirmation.current!!.title)
        assertEquals(emptyList<String>(), removed)
    }

    @Test
    fun confirmRemovesThatEntryOnceAndReturnsToList() {
        coordinator.onLongPress("A")
        confirmation.confirm()
        confirmation.confirm() // a double tap must not remove twice
        assertEquals(listOf("A"), removed)
        assertEquals(RemovalState.List, coordinator.state.value)
        assertNull(confirmation.current)
    }

    @Test
    fun declineWritesNothingAndReturnsToList() {
        coordinator.onLongPress("A")
        confirmation.decline()
        assertEquals(emptyList<String>(), removed)
        assertEquals(RemovalState.List, coordinator.state.value)
        assertNull(confirmation.current)
    }

    @Test
    fun anotherRowIsIgnoredWhileOneIsPending() {
        coordinator.onLongPress("A")
        coordinator.onLongPress("B")
        assertEquals(RemovalState.ConfirmingRemoval("A"), coordinator.state.value)
        confirmation.confirm()
        assertEquals(listOf("A"), removed) // B was never asked about
    }

    @Test
    fun aRowCanBeLongPressedAgainAfterTheAnswer() {
        coordinator.onLongPress("A")
        confirmation.decline()
        coordinator.onLongPress("B")
        assertNotNull(confirmation.current)
        confirmation.confirm()
        assertEquals(listOf("B"), removed)
    }
}
