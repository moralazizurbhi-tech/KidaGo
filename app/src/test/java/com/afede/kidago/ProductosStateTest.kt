package com.afede.kidago

import com.afede.kidago.data.ListEntry
import com.afede.kidago.ui.products.productosState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductosStateTest {
    private val stored = listOf(ListEntry(1, "1111111111111", 2), ListEntry(2, "2222222222222", 1), ListEntry(3, "3333333333333", 4))

    @Test
    fun showsMostRecentlyAddedFirstWhateverTheQuantities() {
        assertEquals(listOf(3L, 2L, 1L), productosState(stored, 7).entries.map { it.seq })
    }

    @Test
    fun carriesTheTotalForTheHeader() {
        assertEquals(7, productosState(stored, 7).total)
    }

    @Test
    fun emptyListIsEmptyWithZeroCount() {
        val state = productosState(emptyList(), 0)
        assertTrue(state.isEmpty)
        assertEquals(0, state.total)
    }
}
