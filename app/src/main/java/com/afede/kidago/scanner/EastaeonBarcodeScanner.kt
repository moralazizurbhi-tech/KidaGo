package com.afede.kidago.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** PDA YGF-F7 (Eastaeon `scandecoderservice`): the vendor service broadcasts each decode; no SDK needed. */
class EastaeonBarcodeScanner(private val context: Context) : BarcodeScanner {
    override val scans: Flow<ScanResult> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val code = intent.getStringExtra(EXTRA_DATA)
                trySend(if (intent.action == ACTION_FAIL || code.isNullOrEmpty()) ScanResult.Failure else ScanResult.Code(toEan13(code)))
            }
        }
        val filter = IntentFilter().apply { addAction(ACTION_SCAN); addAction(ACTION_FAIL) }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }

    private companion object {
        /** The service reports a UPC-A as 12 digits; the catalog keys on EAN-13 (TASK-031). */
        fun toEan13(code: String) = if (code.length == 12 && code.all(Char::isDigit)) "0$code" else code

        // The service also sends a second, newline-terminated broadcast on com.android.receive_scan_action for the same scan; listening to both counts it twice.
        const val ACTION_SCAN = "com.android.ygf.receive_scan_action"
        const val ACTION_FAIL = "com.android.receive_scan_fail_action"
        const val EXTRA_DATA = "data"
    }
}
