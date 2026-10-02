package com.afede.kidago.data

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Settings(val soundEnabled: Boolean, val vibrationEnabled: Boolean, val catalogFolder: String)

/**
 * Device-local app settings (FEAT-004 TD). Every write is persisted before it returns (commit, not apply);
 * the values are tiny and written only on a user tap. SharedPreferences: the TD leaves the mechanism open
 * and the platform provides it, so no new dependency.
 */
class SettingsStore(
    context: Context,
    private val defaultCatalogFolder: String = defaultCatalogFolder(),
    prefsName: String = "settings",
) {
    private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private val state = MutableStateFlow(read())

    /** Current values; updates on every write. */
    val settings: StateFlow<Settings> = state

    fun setSoundEnabled(enabled: Boolean) = write { putBoolean(SOUND, enabled) }

    fun setVibrationEnabled(enabled: Boolean) = write { putBoolean(VIBRATION, enabled) }

    /** Saves a non-empty folder and returns true; an empty or blank value is rejected and nothing changes. */
    fun setCatalogFolder(folder: String): Boolean {
        if (folder.isBlank()) return false
        write { putString(FOLDER, folder) }
        return true
    }

    private fun write(change: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(change).commit()
        state.value = read()
    }

    private fun read() = Settings(
        soundEnabled = prefs.getBoolean(SOUND, true),
        vibrationEnabled = prefs.getBoolean(VIBRATION, true),
        catalogFolder = prefs.getString(FOLDER, null) ?: defaultCatalogFolder,
    )

    companion object {
        private const val SOUND = "sound"
        private const val VIBRATION = "vibration"
        private const val FOLDER = "catalogFolder"

        /** `<shared-storage root>/afede/kidago/` */
        fun defaultCatalogFolder(): String = Environment.getExternalStorageDirectory().path + "/afede/kidago/"
    }
}
