package com.afede.kidago.data

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the platform says about the USB link (FEAT-004 C14). */
data class UsbSnapshot(val charging: Boolean = false, val connected: Boolean = false, val fileTransfer: Boolean = false)

enum class UsbStatus { Unplugged, ChargingOnly, ConnectedNoFiles, FileTransfer }

fun UsbSnapshot.status() = when {
    connected && fileTransfer -> UsbStatus.FileTransfer
    connected -> UsbStatus.ConnectedNoFiles
    charging -> UsbStatus.ChargingOnly // power but no data link: the case where Android greys out the USB options
    else -> UsbStatus.Unplugged
}

/**
 * The USB state and its history since the app started, for the "Conexión USB" help screen (FEAT-004 C14, C15). Only a
 * change is recorded: each source keeps its last value, so the battery's frequent level updates add nothing.
 */
class UsbConnectionLog(private val now: () -> Long = System::currentTimeMillis, private val max: Int = 300) {
    private val _snapshot = MutableStateFlow(UsbSnapshot())
    val snapshot: StateFlow<UsbSnapshot> = _snapshot

    private val lines = ArrayDeque<String>()
    private val last = mutableMapOf<String, String>()

    @Synchronized
    fun record(source: String, value: String, update: (UsbSnapshot) -> UsbSnapshot = { it }) {
        _snapshot.value = update(_snapshot.value)
        if (last.put(source, value) == value) return
        lines.addLast("${stamp(now())}  $source: $value")
        if (lines.size > max) lines.removeFirst()
    }

    @Synchronized
    fun history(): List<String> = lines.toList()

    companion object {
        fun stamp(millis: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(millis))
    }
}

/**
 * Feeds [log] from the two sticky system broadcasts that describe the cable: USB_STATE (data link and its functions)
 * and the battery's plugged source. A normal app cannot change or restart the USB mode (MANAGE_USB is a system
 * permission), so this only observes.
 */
// ponytail: listens for the process lifetime; while the app is cached Android may hold back and merge these
// broadcasts, so the history can miss quick changes made in the background. The current state is always right.
class AndroidUsbMonitor(private val context: Context, private val log: UsbConnectionLog) {
    fun start() {
        ContextCompat.registerReceiver(context, receiver, IntentFilter(USB_STATE).apply { addAction(Intent.ACTION_BATTERY_CHANGED) }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                USB_STATE -> {
                    val extras = intent.extras
                    @Suppress("DEPRECATION") // every extra, whatever its type, goes to the log as text
                    val all = extras?.keySet()?.sorted()?.joinToString(" ") { "$it=${extras.get(it)}" }.orEmpty()
                    log.record("USB", all) { it.copy(connected = intent.getBooleanExtra("connected", false), fileTransfer = intent.getBooleanExtra("mtp", false)) }
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                    val name = when (plugged) {
                        0 -> "sin cable"
                        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                        BatteryManager.BATTERY_PLUGGED_AC -> "cargador"
                        else -> "otro ($plugged)"
                    }
                    log.record("Alimentación", name) { it.copy(charging = plugged != 0) }
                }
            }
        }
    }

    /** The log as a text file in Descargas, indexed at once so it also shows over MTP. Returns its name, or null. */
    fun save(version: String): String? = runCatching {
        val now = System.currentTimeMillis()
        val name = "KidaGo-usb-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date(now)) + ".txt"
        val s = log.snapshot.value
        val text = buildString {
            appendLine("KidaGo v$version · registro USB · ${UsbConnectionLog.stamp(now)}")
            appendLine("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Opciones de desarrollador: ${global(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED)} · Depuración USB: ${global(Settings.Global.ADB_ENABLED)}")
            appendLine("Estado: ${s.status()} (carga=${s.charging}, datos=${s.connected}, transferencia de archivos=${s.fileTransfer})")
            appendLine()
            appendLine("Historial desde que se abrió la app:")
            log.history().forEach(::appendLine)
        }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
        resolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) }
        name
    }.getOrNull()

    private fun global(key: String) = if (Settings.Global.getInt(context.contentResolver, key, 0) == 1) "sí" else "no"

    private companion object {
        const val USB_STATE = "android.hardware.usb.action.USB_STATE" // hidden UsbManager.ACTION_USB_STATE, sticky
    }
}
