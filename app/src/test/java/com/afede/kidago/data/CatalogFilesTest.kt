package com.afede.kidago.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CatalogFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    private val detector = CatalogFileDetector()
    private val cleanup = CatalogFileCleanup()

    private fun touch(name: String) = File(tmp.root, name).apply { writeText("1234567890123\n") }
    private fun candidate() = (detector.detect(tmp.root) as Detection.CandidatePresent).candidate

    @Test
    fun newestDateWinsAndOlderOnesAreReported() {
        touch("product-catalog-2026-01-05.txt")
        touch("product-catalog-2026-03-01.txt")
        touch("product-catalog-2025-12-31.txt")
        val c = candidate()
        assertEquals("product-catalog-2026-03-01.txt", c.file.name)
        assertEquals(setOf("product-catalog-2026-01-05.txt", "product-catalog-2025-12-31.txt"), c.older.map { it.name }.toSet())
    }

    @Test
    fun newestIsByDateNotByNameOrder() {
        touch("product-catalog-2026-02-10.txt")
        touch("product-catalog-2026-10-02.txt")
        assertEquals("product-catalog-2026-10-02.txt", candidate().file.name)
    }

    @Test
    fun onlyMatchingNamesAreConsidered() {
        touch("catalog.txt")
        touch("product-catalog-2026-1-5.txt")
        touch("product-catalog-2026-13-45.txt") // not a real date
        touch("product-catalog-2026-01-05.csv")
        touch("old-product-catalog-2026-01-05.txt")
        File(tmp.root, "product-catalog-2026-01-06.txt").mkdir() // a directory is not a file
        assertEquals(Detection.NoCandidate, detector.detect(tmp.root))
    }

    @Test
    fun emptyFolderHasNoCandidate() {
        assertEquals(Detection.NoCandidate, detector.detect(tmp.root))
    }

    @Test
    fun missingFolderIsCreatedSilentlyAndNoImportBegins() {
        val folder = File(tmp.root, "afede/kidago")
        assertEquals(Detection.NoCandidate, detector.detect(folder))
        assertTrue(folder.isDirectory)
    }

    @Test
    fun cleanupDeletesImportedAndOlderButNeverOtherFiles() {
        touch("product-catalog-2026-01-05.txt")
        touch("product-catalog-2026-03-01.txt")
        val other = touch("notes.txt")
        val failed = cleanup.delete(candidate())
        assertTrue(failed.isEmpty())
        assertEquals(listOf("notes.txt"), tmp.root.list()!!.toList())
        assertTrue(other.exists())
    }

    @Test
    fun cleanupNeverDeletesANonMatchingFileEvenIfHandedOne() {
        val other = touch("notes.txt")
        cleanup.delete(CatalogCandidate(other, emptyList()))
        assertTrue(other.exists())
    }

    @Test
    fun failedDeletionIsReportedNotThrownAndTheRestStillGoes() {
        val stuck = File(tmp.root, "product-catalog-2026-01-05.txt").apply {
            mkdir() // a non-empty directory cannot be deleted by File.delete()
            File(this, "x").writeText("x")
        }
        val newest = touch("product-catalog-2026-03-01.txt")
        val failed = cleanup.delete(CatalogCandidate(newest, listOf(stuck)))
        assertEquals(listOf(stuck), failed)
        assertFalse(newest.exists())
    }
}
