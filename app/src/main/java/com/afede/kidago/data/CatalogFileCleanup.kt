package com.afede.kidago.data

import java.io.File

/**
 * Deletes the imported catalog file and the older ones after a successful import (FEAT-001 TD, CatalogFileCleanup).
 * The caller invokes it only after the store committed; it is never invoked after a failed import (C13).
 */
class CatalogFileCleanup {

    /**
     * Returns the files that could not be deleted. A failed deletion never throws and never undoes the import:
     * the file stays and is found again at the next detection opportunity. Files whose name does not match the
     * catalog pattern are never deleted.
     */
    fun delete(candidate: CatalogCandidate): List<File> =
        (listOf(candidate.file) + candidate.older)
            .filter { CatalogFileName.matches(it.name) }
            .filterNot { file -> runCatching { file.delete() || !file.exists() }.getOrDefault(false) }
}
