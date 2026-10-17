package com.afede.kidago.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.io.FileOutputStream

/**
 * Keeps `scanned-list.txt` equal to the current list and recognizes an export reset (FEAT-002 TD: ListFileMirror and
 * ExportResetDetector, one unit because their order is the whole point). At each launch/resume, [onLaunchOrResume]
 * first looks at the file — empty while the list has entries means the desktop tool exported, so the list is
 * archived and emptied (C14) — and only then starts mirroring (C13): the whole file is rewritten from the store after
 * every change, never before that first pass, so the mirror cannot overwrite a cleared file and hide the export.
 * While file access is missing nothing is read or written (C19); the next launch/resume after the grant realigns it.
 */
class ListFileMirror(
    private val file: () -> File,
    private val entries: Flow<List<ListEntry>>,
    private val hasAccess: () -> Boolean,
    private val archiveForExport: suspend () -> Boolean,
) {
    private val lock = Mutex() // one file operation at a time; each write reads the list afresh under it
    private var mirroring = false

    /** Follows every change to the list. Call once; it does nothing until the first [onLaunchOrResume] pass succeeds. */
    fun start(scope: CoroutineScope) {
        scope.launch { entries.collect { if (mirroring) rewrite() } }
    }

    suspend fun onLaunchOrResume() = lock.withLock {
        mirroring = false
        if (!hasAccess()) return // C19: cannot read or write; mirroring resumes at the next pass after the grant
        try {
            // C14: an existing file with no lines, while the list has entries. A missing file is not "cleared".
            if (file().exists() && file().readLines().none { it.isNotBlank() } && entries.first().isNotEmpty()) archiveForExport()
            write(entries.first())
            mirroring = true
        } catch (_: IOException) {
            // the file could not be read or written: leave it alone, the next launch/resume tries again
        }
    }

    /**
     * The same export-reset check as [onLaunchOrResume], for while the app stays in the foreground: the desktop tool
     * can empty the file at any moment and the app never resumes. Does nothing before the first launch pass, without
     * access, or when the file is not empty; runs under the same lock as the mirror writes, so it never sees a file
     * the mirror is in the middle of rewriting.
     */
    suspend fun checkForExport() = lock.withLock {
        if (!mirroring || !hasAccess()) return
        try {
            if (file().exists() && file().readLines().none { it.isNotBlank() } && entries.first().isNotEmpty()) {
                archiveForExport()
                write(entries.first())
            }
        } catch (_: IOException) {
            // the next check tries again
        }
    }

    private suspend fun rewrite() = lock.withLock {
        if (!hasAccess()) return
        try {
            write(entries.first())
        } catch (_: IOException) {
            // a failed write heals on the next change
        }
    }

    /**
     * The whole file, rewritten in place in a single write. A rename-over (temp file, then move) would make a partial
     * file impossible to see, but the platform's media index never learns the new size of a file replaced that way, and
     * the desktop tool, reading over MTP, then sees the old (empty) list and empties the PDA's copy. The list is a few
     * hundred bytes, so the window in which a reader could see it half-written is negligible.
     */
    private fun write(list: List<ListEntry>) {
        val target = file()
        target.parentFile?.mkdirs()
        FileOutputStream(target).use { out ->
            out.write(list.joinToString("") { "${it.code},${it.quantity}\n" }.toByteArray())
            out.fd.sync()
        }
    }

    companion object {
        const val FILE_NAME = "scanned-list.txt"
    }
}
