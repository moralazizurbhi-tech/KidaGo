package com.afede.kidago.data

import kotlinx.coroutines.flow.StateFlow

sealed interface LookupResult {
    data object Found : LookupResult
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
    private val contains: suspend (String) -> Boolean,
) {
    suspend fun lookup(barcode: String): LookupResult = when {
        state.value is SyncState.Syncing -> LookupResult.Unavailable
        contains(barcode) -> LookupResult.Found
        else -> LookupResult.NotFound
    }
}
