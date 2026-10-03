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
 * a bad file leaves the previous catalog untouched (C5, C13).
 */
class CatalogImporter(private val commit: suspend (List<String>) -> Unit) {

    /** [onProgress] receives the fraction of the file read, 0f..1f, from the reading thread. */
    suspend fun import(file: File, onProgress: (Float) -> Unit = {}): ImportResult = withContext(Dispatchers.IO) {
        val records = ArrayList<String>()
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
                    if (isValid(line)) {
                        records += line
                    } else {
                        failures++
                        if (diagnostics.size < MAX_DIAGNOSTICS) diagnostics += RecordDiagnostic(lineNumber, line, reasonFor(line))
                    }
                    onProgress((read.toFloat() / total).coerceAtMost(1f))
                }
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

    // ASCII digits only: Char.isDigit() would accept other scripts' digits.
    private fun isValid(line: String) = line.length == 13 && line.all { it in '0'..'9' }

    private fun reasonFor(line: String) = when {
        line.isEmpty() -> "línea vacía"
        line.any { it !in '0'..'9' } -> "contiene caracteres que no son dígitos"
        else -> "debe tener exactamente 13 dígitos, tiene ${line.length}"
    }

    companion object {
        const val MAX_DIAGNOSTICS = 10 // the strip panel grows to fit and the screen does not scroll, so keep it short
        private const val BOM = "\uFEFF"
    }
}
