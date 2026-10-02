package com.afede.kidago

import android.content.Context
import androidx.room.Room
import com.afede.kidago.data.AppDatabase
import com.afede.kidago.scanner.BarcodeScanner
import com.afede.kidago.scanner.FakeBarcodeScanner

/** Manual dependency injection: the single place that picks implementations. */
class AppContainer(
    context: Context,
    val scanner: BarcodeScanner = FakeBarcodeScanner(), // swapped for the vendor SDK implementation later
) {
    val database: AppDatabase =
        Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "kidago.db").build()
}
