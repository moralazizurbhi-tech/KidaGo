package com.afede.kidago.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BarcodeDisplayTest {
    @Test
    fun a13DigitCodeIsShownGrouped1_6_6() = assertEquals("8 412345 678905", groupedBarcode("8412345678905"))

    @Test
    fun aPartialCodeIsGroupedAsFarAsItGoes() {
        assertEquals("", groupedBarcode(""))
        assertEquals("8 4123", groupedBarcode("84123"))
        assertEquals("8 412345 67", groupedBarcode("841234567"))
    }

    @Test
    fun theValueItselfStaysDigitsOnly() {
        val stored = "8412345678905"
        val shown = groupedBarcode(stored)
        assertEquals("8412345678905", stored) // the input is never modified
        assertEquals(stored, shown.replace(" ", "")) // grouping adds spaces and nothing else
    }
}
