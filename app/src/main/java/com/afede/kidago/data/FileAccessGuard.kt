package com.afede.kidago.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings

/**
 * The "all files access" check and its settings screen (FEAT-001 TD). Observation-based: the access state is read
 * live, and the user is only sent to settings at launch/resume ([checkAtLaunchOrResume]) or on the strip tap
 * ([openSettings]). Callers own the timing; nothing here polls or listens.
 */
class FileAccessGuard(
    private val hasAccess: () -> Boolean,
    private val showSettings: () -> Unit,
) {
    /** Current state, read live from the platform. */
    fun isGranted(): Boolean = hasAccess()

    /** Launch/resume check: reports the state and, when the grant is missing, takes the user to the settings screen. */
    fun checkAtLaunchOrResume(): Boolean {
        val granted = hasAccess()
        if (!granted) showSettings()
        return granted
    }

    /** The user tapped the warning strip. */
    fun openSettings() = showSettings()

    companion object {
        fun forContext(context: Context): FileAccessGuard {
            val app = context.applicationContext
            return FileAccessGuard(
                hasAccess = Environment::isExternalStorageManager,
                showSettings = {
                    val forThisApp = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${app.packageName}"))
                    val general = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    try {
                        app.startActivity(forThisApp.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: android.content.ActivityNotFoundException) {
                        app.startActivity(general.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                },
            )
        }
    }
}
