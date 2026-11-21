package com.afede.kidago.data

import kotlinx.coroutines.flow.StateFlow

sealed interface LookupResult {
    /** In the catalog; [priceCents] is null when its record has no price (FEAT-001 C25). */
    data class Found(val priceCents: Long? = null) : LookupResult
    data object NotFound : LookupResult

    /** The catalog is being replaced; capture must wait (C4). */
    data object Unavailable : LookupResult
}

/**
 * The one entry point for catalog lookups (FEAT-001 TD, CatalogQueryFacade). Refuses only while Syncing, without
 * consulting the store; in every other state, No File Access included, it answers from the last good catalog (C20).
 */
class CatalogQueryFacade(
    private val state: StateFlow<SyncState>,
    private val find: suspend (String) -> CatalogItem?,
) {
    suspend fun lookup(barcode: String): LookupResult {
        if (state.value is SyncState.Syncing) return LookupResult.Unavailable
        val item = find(barcode) ?: return LookupResult.NotFound
        return LookupResult.Found(item.priceCents)
    }
}

/** Spanish euros from cents, e.g. 2499 → "24,99 €" (no-break space before the sign). Display only (FEAT-002 C24). */
fun formatPrice(cents: Long): String = "%d,%02d €".format(cents / 100, cents % 100)
