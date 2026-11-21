package com.afede.kidago.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** FEAT-004 C14, C15: the USB log keeps only changes, per source, and the latest state. */
class UsbConnectionLogTest {
    @Test
    fun onlyChangesAreRecordedAndTheStateFollows() {
        val log = UsbConnectionLog(now = { 0L }, max = 3)
        log.record("Alimentación", "USB") { it.copy(charging = true) }
        log.record("Alimentación", "USB") { it.copy(charging = true) } // a battery level update: nothing new
        assertEquals(UsbStatus.ChargingOnly, log.snapshot.value.status()) // the greyed-out case: power, no data
        log.record("USB", "connected=true mtp=false") { it.copy(connected = true) }
        assertEquals(UsbStatus.ConnectedNoFiles, log.snapshot.value.status())
        log.record("USB", "connected=true mtp=true") { it.copy(fileTransfer = true) }
        assertEquals(UsbStatus.FileTransfer, log.snapshot.value.status())
        log.record("Alimentación", "sin cable") { UsbSnapshot() }
        assertEquals(UsbStatus.Unplugged, log.snapshot.value.status())
        assertEquals(listOf("USB: connected=true mtp=false", "USB: connected=true mtp=true", "Alimentación: sin cable"), log.history().map { it.substringAfter("  ") })
    }
}
