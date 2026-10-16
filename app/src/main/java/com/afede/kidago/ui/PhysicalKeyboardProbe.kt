package com.afede.kidago.ui

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the device has a hardware input device able to type the digits 0 to 9 (FEAT-002 Technical Design,
 * PhysicalKeyboardProbe; Project Architecture, Input environment). It only conditions the on-screen keyboard and
 * never blocks capture. [readDevices] answers, per input device, whether it types every digit; null when the
 * platform cannot answer. "Absent" comes only from a positive answer, so null, an empty list or a failure give "present".
 */
class PhysicalKeyboardProbe(private val readDevices: () -> List<Boolean>?) {
    private val _present = MutableStateFlow(evaluate())
    val present: StateFlow<Boolean> = _present

    /** Re-evaluates: called at launch (construction), at resume and when the platform reports an input-device change. */
    fun refresh() {
        _present.value = evaluate()
    }

    private fun evaluate(): Boolean {
        val devices = try { readDevices() } catch (_: Exception) { null }
        return devices.isNullOrEmpty() || devices.any { it }
    }

    companion object {
        private val DIGITS = IntArray(10) { KeyEvent.KEYCODE_0 + it }

        fun forContext(context: Context): PhysicalKeyboardProbe {
            val probe = PhysicalKeyboardProbe {
                InputDevice.getDeviceIds().asList().mapNotNull { InputDevice.getDevice(it) }
                    .filterNot { it.isVirtual }
                    .map { device -> device.hasKeys(*DIGITS).all { it } }
            }
            context.applicationContext.getSystemService(InputManager::class.java)?.registerInputDeviceListener(
                object : InputManager.InputDeviceListener {
                    override fun onInputDeviceAdded(deviceId: Int) = probe.refresh()
                    override fun onInputDeviceRemoved(deviceId: Int) = probe.refresh()
                    override fun onInputDeviceChanged(deviceId: Int) = probe.refresh()
                },
                Handler(Looper.getMainLooper()),
            )
            return probe
        }
    }
}
