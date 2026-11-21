package com.afede.kidago.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** One rejected line: where it was, what it said and why it failed (FEAT-001 C6). */
data class RecordDiagnostic(val lineNumber: Int, val content: String, val reason: String)

sealed interface ImportResult {
    data class Success(val recordCount: Int) : ImportResult

    /** [diagnostics] holds the first [CatalogImporter.MAX_DIAGNOSTICS] failures; [failureCount] is the full count. */
    data class Failure(val diagnostics: List<RecordDiagnostic>, val failureCount: Int) : ImportResult
}

/**
 * Reads and validates a candidate catalog file in one pass; applies nothing until the whole file is valid
 * (FEAT-001 TD, CatalogImporter). [commit] is the store's atomic replace; it is never called on failure, so
 * a bad file leaves the previous catalog untouched (C5, C13). A line is `code` or `code;price` (C15, C24).
 */
class CatalogImporter(private val commit: suspend (List<CatalogItem>) -> Unit) {

    /** [onProgress] receives the fraction of the file read, 0f..1f, from the reading thread. */
    suspend fun import(file: File, onProgress: (Float) -> Unit = {}): ImportResult = withContext(Dispatchers.IO) {
        val records = ArrayList<CatalogItem>()
        val diagnostics = ArrayList<RecordDiagnostic>()
        var failures = 0
        try {
            val total = file.length().coerceAtLeast(1)
            var read = 0L
            var lineNumber = 0
            file.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (raw in lines) {
                    lineNumber++
                    read += raw.toByteArray(Charsets.UTF_8).size + 1 // ponytail: assumes 1-byte line ends, progress is approximate
                    val line = if (lineNumber == 1) raw.removePrefix(BOM) else raw
                    val record = parse(line)
                    if (record != null) {
                        records += record
                    } else {
                        failures++
                        if (diagnostics.size < MAX_DIAGNOSTICS) diagnostics += RecordDiagnostic(lineNumber, line, reasonFor(line))
                    }
                    onProgress((read.toFloat() / total).coerceAtMost(1f))
                }
            }
            if (lineNumber == 0) { // no records at all fails like a malformed file, never wipes the catalog (C23)
                return@withContext ImportResult.Failure(listOf(RecordDiagnostic(0, file.name, "el archivo no contiene registros")), 1)
            }
            if (failures > 0) return@withContext ImportResult.Failure(diagnostics, failures)
            commit(records)
            onProgress(1f)
            ImportResult.Success(records.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) { // unreadable file or failed commit (the store rolls back): a failed import, nothing applied
            ImportResult.Failure(listOf(RecordDiagnostic(0, file.name, "No se pudo leer o aplicar el archivo: ${e.message}")), 1)
        }
    }

    /** The record of a valid line, or null when the line is malformed. */
    private fun parse(line: String): CatalogItem? {
        val (code, price) = split(line)
        if (!isValidCode(code)) return null
        if (price == null) return CatalogItem(code)
        return priceCents(price)?.let { CatalogItem(code, it) }
    }

    private fun reasonFor(line: String): String {
        val (code, price) = split(line)
        return when {
            line.isEmpty() -> "línea vacía"
            code.any { it !in '0'..'9' } -> "el código contiene caracteres que no son dígitos"
            !isValidCode(code) -> "el código debe tener 13 dígitos (o menos de 12), tiene ${code.length}"
            else -> "precio no válido: \"$price\" (por ejemplo 24.99)"
        }
    }

    companion object {
        const val MAX_DIAGNOSTICS = 10 // the strip panel grows to fit and the screen does not scroll, so keep it short
        private const val BOM = "﻿"
        private val PRICE = Regex("""(\d{1,9})(?:[.,](\d{1,2}))?""")

        /** Code and the text after the first `;` or `,` (null when there is no separator). */
        fun split(line: String): Pair<String, String?> {
            val at = line.indexOfFirst { it == ';' || it == ',' }
            return if (at < 0) line to null else line.substring(0, at) to line.substring(at + 1)
        }

        // ASCII digits only: Char.isDigit() would accept other scripts' digits.
        // Shorter than 12 is kept as is (typed by hand on the scanner); 12 must have been padded to 13 by the desktop app.
        fun isValidCode(code: String) = code.isNotEmpty() && (code.length < 12 || code.length == 13) && code.all { it in '0'..'9' }

        /** Digits with at most two decimals after `.` or `,`, in whole cents, never rounded; null when not of that form. */
        fun priceCents(text: String): Long? {
            val m = PRICE.matchEntire(text) ?: return null
            return m.groupValues[1].toLong() * 100 + m.groupValues[2].padEnd(2, '0').toLong()
        }
    }
}
