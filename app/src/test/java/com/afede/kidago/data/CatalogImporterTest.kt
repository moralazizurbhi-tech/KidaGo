package com.afede.kidago.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CatalogImporterTest {
    @get:Rule val tmp = TemporaryFolder()

    private var committed: List<String>? = null
    private var commitCalls = 0
    private val importer = CatalogImporter { committed = it; commitCalls++ }

    private fun file(text: String): File = tmp.newFile().apply { writeText(text) }

    @Test
    fun validFileIsCommittedWhole() = runTest {
        val result = importer.import(file("1234567890123\n9876543210987\n"))
        assertEquals(ImportResult.Success(2), result)
        assertEquals(listOf("1234567890123", "9876543210987"), committed)
    }

    @Test
    fun crlfAndMissingTrailingNewlineAreAccepted() = runTest {
        assertEquals(ImportResult.Success(2), importer.import(file("1234567890123\r\n9876543210987")))
    }

    @Test
    fun badLinesDiscardEverythingAndNeverCommit() = runTest {
        val result = importer.import(file("1234567890123\n123456789012\n12345678901234\n12345678901ab\n\n9876543210987\n"))
        result as ImportResult.Failure
        assertEquals(0, commitCalls)
        assertEquals(4, result.failureCount)
        assertEquals(listOf(2, 3, 4, 5), result.diagnostics.map { it.lineNumber })
        assertEquals("123456789012", result.diagnostics[0].content)
        assertTrue(result.diagnostics.all { it.reason.isNotBlank() })
    }

    @Test
    fun nonAsciiDigitsAreMalformed() = runTest {
        assertTrue(importer.import(file("١٢٣٤٥٦٧٨٩٠١٢٣")) is ImportResult.Failure) // 13 Arabic-Indic digits
    }

    @Test
    fun diagnosticsAreCappedButCountIsComplete() = runTest {
        val result = importer.import(file("x\n".repeat(CatalogImporter.MAX_DIAGNOSTICS + 10))) as ImportResult.Failure
        assertEquals(CatalogImporter.MAX_DIAGNOSTICS, result.diagnostics.size)
        assertEquals(CatalogImporter.MAX_DIAGNOSTICS + 10, result.failureCount)
    }

    @Test
    fun progressIsReportedAndEndsAtOne() = runTest {
        val seen = mutableListOf<Float>()
        importer.import(file("1234567890123\n".repeat(10)), seen::add)
        assertTrue(seen.size >= 10)
        assertEquals(seen.sorted(), seen)
        assertEquals(1f, seen.last(), 0f)
    }

    @Test
    fun unreadableFileIsAFailureNotACrash() = runTest {
        val result = importer.import(File(tmp.root, "missing.txt"))
        assertTrue(result is ImportResult.Failure)
        assertEquals(0, commitCalls)
    }

    @Test
    fun failedCommitIsAFailure() = runTest {
        val failing = CatalogImporter { error("disk full") }
        assertTrue(failing.import(file("1234567890123\n")) is ImportResult.Failure)
    }
}
