package com.afede.kidago.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afede.kidago.data.Settings
import com.afede.kidago.ui.components.ConfirmationController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What Ajustes shows. [version] is the bare version name; the screen formats it as "KidaGo v{version}". */
data class AjustesState(val soundEnabled: Boolean, val vibrationEnabled: Boolean, val catalogFolder: String, val version: String)

/**
 * Ajustes' state holder (FEAT-004 TD): a pass-through over the SettingsStore, not a second source of truth.
 * Every write goes straight to the store (no save step, C4); each toggle writes only its own flag (C13).
 */
class AjustesViewModel(
    settings: StateFlow<Settings>,
    private val setSound: (Boolean) -> Unit,
    private val setVibration: (Boolean) -> Unit,
    private val setFolder: (String) -> Boolean,
    private val version: String,
    archiveAndClear: suspend () -> Unit,
) : ViewModel() {
    val confirmation = ConfirmationController()
    val clear = ListClearCoordinator(confirmation, viewModelScope, archiveAndClear)

    /** While a clear confirmation is pending no other Ajustes action proceeds (C9). */
    private val idle get() = clear.state.value == ClearState.Idle

    private fun Settings.toState() = AjustesState(soundEnabled, vibrationEnabled, catalogFolder, version)

    val state: StateFlow<AjustesState> =
        settings.map { it.toState() }.stateIn(viewModelScope, SharingStarted.Eagerly, settings.value.toState())

    fun onSoundToggle() { if (idle) setSound(!state.value.soundEnabled) }

    fun onVibrationToggle() { if (idle) setVibration(!state.value.vibrationEnabled) }

    /** Returns true when saved; an empty value is rejected by the store and the previous folder stays (C11). */
    fun onFolderSubmit(folder: String): Boolean = idle && setFolder(folder)
}
