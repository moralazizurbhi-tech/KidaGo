package com.afede.kidago.data

import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** The chosen catalog file and the older matching files, all of which cleanup deletes after a successful import. */
data class CatalogCandidate(val file: File, val older: List<File>)

sealed interface Detection {
    data object NoCandidate : Detection
    data class CandidatePresent(val candidate: CatalogCandidate) : Detection
}

/** Catalog file naming: `product-catalog-yyyy-MM-dd.txt` (FEAT-001 TD, File Handoff). */
internal object CatalogFileName {
    private val pattern = Regex("""product-catalog-(\d{4}-\d{2}-\d{2})\.txt""")

    /** The date in a matching name, or null when the name does not match or the date does not exist. */
    fun dateOf(name: String): LocalDate? {
        val iso = pattern.matchEntire(name)?.groupValues?.get(1) ?: return null
        return try { LocalDate.parse(iso) } catch (_: DateTimeParseException) { null }
    }

    fun matches(name: String) = dateOf(name) != null
}

/** Finds the newest catalog file in the catalog folder at a detection opportunity (FEAT-001 TD, CatalogFileDetector). */
class CatalogFileDetector {

    /** Creates a missing [folder] silently (C16) and begins no import; otherwise picks the newest matching file (C11). */
    fun detect(folder: File): Detection {
        if (!folder.exists()) {
            folder.mkdirs() // silent by design; if it cannot be created there is simply nothing to import
            return Detection.NoCandidate
        }
        val matching = folder.listFiles { f -> f.isFile && CatalogFileName.matches(f.name) }
            ?.sortedByDescending { CatalogFileName.dateOf(it.name) }
            .orEmpty()
        if (matching.isEmpty()) return Detection.NoCandidate
        return Detection.CandidatePresent(CatalogCandidate(matching.first(), matching.drop(1)))
    }
}
