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
    private var items: List<CatalogItem>? = null
    private var commitCalls = 0
    private val importer = CatalogImporter { items = it; committed = it.map(CatalogItem::barcode); commitCalls++ }

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
    fun shortLinesAreImportedAsIs() = runTest {
        assertEquals(ImportResult.Success(2), importer.import(file("4444\n1234567890123\n")))
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
    fun emptyFileFailsAndNeverCommits() = runTest {
        val result = importer.import(file("")) as ImportResult.Failure
        assertEquals(0, commitCalls)
        assertEquals(1, result.failureCount)
        assertTrue(result.diagnostics.single().reason.contains("no contiene registros"))
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

    @Test
    fun pricedLinesCarryTheirPriceInCents() = runTest {
        val result = importer.import(file("0000772190817;24.99\r\n0000772190818;24,99\r\n0000772190819,3\r\n8412345678901;0.00\r\n8412345678902\r\n"))
        assertEquals(ImportResult.Success(5), result)
        assertEquals(listOf(2499L, 2499L, 300L, 0L, null), items!!.map { it.priceCents })
    }

    @Test
    fun aMalformedPriceFailsTheWholeImport() = runTest {
        for (bad in listOf("8412345678901;abc", "8412345678901;", "8412345678901;1.999", "8412345678901;-2", "8412345678901;2 €")) {
            val result = importer.import(file("1234567890123;1.00\n$bad\n"))
            assertTrue(bad, result is ImportResult.Failure && result.diagnostics.single().reason.startsWith("precio no válido"))
        }
        assertEquals(0, commitCalls)
    }

    @Test
    fun aBadCodeIsReportedAsACodeProblemWhateverItsPrice() = runTest {
        val result = importer.import(file("X0016SA1Z7;19.99\n")) as ImportResult.Failure
        assertTrue(result.diagnostics.single().reason.contains("código"))
    }

    @Test
    fun priceParsingIsExactAndNeverRounds() {
        assertEquals(181500L, CatalogImporter.priceCents("1815.00"))
        assertEquals(150L, CatalogImporter.priceCents("1.5"))
        assertEquals(null, CatalogImporter.priceCents("1.555"))
        assertEquals(null, CatalogImporter.priceCents(".50"))
        assertEquals("24,99\u00A0€", formatPrice(2499))
        assertEquals("0,05\u00A0€", formatPrice(5))
    }
}
