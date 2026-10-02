package com.afede.kidago.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmationControllerTest {
    private var confirmed = 0
    private var declined = 0

    private fun request(title: String = "¿Eliminar este producto?") =
        ConfirmationRequest(title, "Eliminar", onConfirm = { confirmed++ }, onDecline = { declined++ })

    @Test
    fun confirmFiresOnceEvenIfCalledTwice() {
        val controller = ConfirmationController()
        controller.request(request())
        controller.confirm()
        controller.confirm()
        assertEquals(1, confirmed)
        assertEquals(0, declined)
        assertNull(controller.current)
    }

    @Test
    fun declineFiresOnceEvenIfCalledTwiceOrAfterConfirm() {
        val controller = ConfirmationController()
        controller.request(request())
        controller.decline()
        controller.decline()
        controller.confirm()
        assertEquals(1, declined)
        assertEquals(0, confirmed)
    }

    @Test
    fun onlyOnePromptAtATimeAndTheFirstIsKept() {
        val controller = ConfirmationController()
        assertTrue(controller.request(request("primero")))
        assertFalse(controller.request(request("segundo")))
        assertEquals("primero", controller.current?.title)
        controller.decline()
        assertTrue(controller.request(request("segundo"))) // free again once resolved
    }

    @Test
    fun resolvingWithNothingPendingDoesNothing() {
        val controller = ConfirmationController()
        controller.confirm()
        controller.decline()
        assertEquals(0, confirmed + declined)
    }
}
