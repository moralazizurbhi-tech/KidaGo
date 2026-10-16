package com.afede.kidago.ui

/**
 * Barcode display rule (Project Architecture, Shared Technical Foundations): a code is shown grouped 1-6-6 digits.
 * Display only; stored and exchanged values stay digits only. A partial code (typing) is grouped as far as it goes.
 */
fun groupedBarcode(digits: String): String =
    buildString { digits.forEachIndexed { i, c -> if (i == 1 || i == 7) append(' '); append(c) } }
