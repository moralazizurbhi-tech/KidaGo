package com.afede.kidago.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import java.io.File
import java.time.Instant

/** What the sync looks like to the rest of the app (FEAT-001 TD, CatalogSyncCoordinator). */
sealed interface SyncState {
    /** [completedAt] is null until the first successful import (the store simply starts empty). */
    data class UpToDate(val completedAt: Instant?) : SyncState

    /** [progress] is the fraction of the file processed, 0f..1f (C3). */
    data class Syncing(val progress: Float) : SyncState

    /** The last import failed; the warning that another Importar may be needed is derived from this state (C14). */
    data class Error(val diagnostics: List<RecordDiagnostic>, val failureCount: Int) : SyncState

    data object NoFileAccess : SyncState
}

/**
 * Owns the sync state and orchestrates one detection opportunity: guard → detect → import → cleanup.
 * Callers invoke [onDetectionOpportunity] at launch and every resume; nothing here polls.
 */
class CatalogSyncCoordinator(
    private val guard: FileAccessGuard,
    private val detector: CatalogFileDetector,
    private val importer: CatalogImporter,
    private val cleanup: CatalogFileCleanup,
    private val catalogFolder: () -> File,
    private val now: () -> Instant = Instant::now,
) {
    private val _state = MutableStateFlow<SyncState>(SyncState.UpToDate(null))
    val state: StateFlow<SyncState> = _state

    // The state the sync rests in when nothing is running and access is fine (UpToDate or Error). No File Access
    // overlays it and, once access is back, hands it back unchanged (C21).
    private var resting: SyncState = _state.value
    private val running = Mutex()

    suspend fun onDetectionOpportunity() {
        if (!running.tryLock()) return // C9: a sync is in progress, never start a second one
        try {
            if (!guard.checkAtLaunchOrResume()) { // C17, C18, C20: no access, detection is skipped
                _state.value = SyncState.NoFileAccess
                return
            }
            _state.value = resting // C19, C21: access is back, the warning clears
            val detection = detector.detect(catalogFolder())
            if (detection !is Detection.CandidatePresent) return // C1: nothing to import, state unchanged
            sync(detection.candidate)
        } finally {
            running.unlock()
        }
    }

    private suspend fun sync(candidate: CatalogCandidate) {
        _state.value = SyncState.Syncing(0f)
        try {
            resting = when (val result = importer.import(candidate.file) { _state.value = SyncState.Syncing(it) }) {
                is ImportResult.Success -> {
                    cleanup.delete(candidate) // only after a committed import (C12, C13); a failed deletion changes nothing
                    SyncState.UpToDate(now())
                }
                is ImportResult.Failure -> SyncState.Error(result.diagnostics, result.failureCount) // file stays, retried at the next opportunity (C8)
            }
        } catch (e: CancellationException) {
            _state.value = resting // never leave the app stuck in Syncing
            throw e
        }
        _state.value = resting
    }
}
