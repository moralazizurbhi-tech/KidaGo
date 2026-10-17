package com.afede.kidago.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ListFileMirrorTest {
    @get:Rule val tmp = TemporaryFolder()

    private val list = MutableStateFlow<List<ListEntry>>(emptyList())
    private var access = true
    private var exports = 0
    private val dir get() = tmp.root.resolve("afede/kidago")
    private val file get() = dir.resolve("scanned-list.txt")

    private fun set(vararg e: Pair<String, Int>) { list.value = e.mapIndexed { i, (c, q) -> ListEntry(i + 1L, c, q) } }

    private fun TestScope.mirror() = ListFileMirror(
        file = { file },
        entries = list,
        hasAccess = { access },
        archiveForExport = { exports++; list.value = emptyList(); true },
    ).also { it.start(backgroundScope) }

    @Test
    fun fileEqualsTheListInInsertionOrderAfterEveryChange() = runTest {
        val m = mirror(); set("A" to 1); m.onLaunchOrResume(); runCurrent()
        assertEquals("A,1\n", file.readText())
        set("A" to 1, "B" to 2); runCurrent()
        assertEquals("A,1\nB,2\n", file.readText())
        set("A" to 3, "B" to 2); runCurrent() // accumulate or typed quantity
        assertEquals("A,3\nB,2\n", file.readText())
        set("B" to 2); runCurrent() // removal
        assertEquals("B,2\n", file.readText())
        set(); runCurrent() // clear
        assertEquals("", file.readText())
        assertEquals(listOf("scanned-list.txt"), dir.list()!!.toList()) // no temp file left behind
    }

    @Test
    fun nothingIsWrittenBeforeTheFirstLaunchPass() = runTest {
        mirror(); set("A" to 1); runCurrent()
        assertFalse(file.exists())
    }

    @Test
    fun anEmptyFileWhileTheListHasEntriesIsAnExportResetAndTheFileStaysEmpty() = runTest {
        dir.mkdirs(); file.writeText("")
        set("A" to 2)
        val m = mirror(); m.onLaunchOrResume(); runCurrent()
        assertEquals(1, exports)
        assertEquals(emptyList<ListEntry>(), list.value)
        assertEquals("", file.readText()) // the mirror did not overwrite the cleared file with the old list
    }

    @Test
    fun aMissingFileIsNotClearedAndIsWritten() = runTest {
        set("A" to 2)
        val m = mirror(); m.onLaunchOrResume(); runCurrent()
        assertEquals(0, exports)
        assertEquals("A,2\n", file.readText())
    }

    @Test
    fun aFileWithLinesOrAnEmptyListTriggersNoReset() = runTest {
        dir.mkdirs(); file.writeText("A,1\n"); set("A" to 1)
        val m = mirror(); m.onLaunchOrResume()
        file.writeText(""); set()
        m.onLaunchOrResume()
        assertEquals(0, exports)
    }

    @Test
    fun withoutAccessNothingIsReadOrWrittenUntilTheNextLaunchPassAfterTheGrant() = runTest {
        access = false
        dir.mkdirs(); file.writeText("")
        set("A" to 1)
        val m = mirror(); m.onLaunchOrResume(); set("A" to 1, "B" to 1); runCurrent()
        assertEquals(0, exports)
        assertEquals("", file.readText())

        access = true
        file.writeText("old,9\n")
        m.onLaunchOrResume(); runCurrent()
        assertEquals("A,1\nB,1\n", file.readText())
    }

    @Test
    fun anExportWhileTheAppStaysInTheForegroundIsDetectedByTheCheck() = runTest {
        set("A" to 2)
        val m = mirror(); m.onLaunchOrResume(); runCurrent()
        m.checkForExport()
        assertEquals(0, exports) // the file still has the list
        file.writeText("") // the desktop tool empties it
        m.checkForExport(); runCurrent()
        assertEquals(1, exports)
        assertEquals(emptyList<ListEntry>(), list.value)
        assertEquals("", file.readText())
    }

    @Test
    fun theCheckDoesNothingBeforeTheFirstLaunchPass() = runTest {
        dir.mkdirs(); file.writeText(""); set("A" to 2)
        val m = mirror(); m.checkForExport()
        assertEquals(0, exports)
    }
}
