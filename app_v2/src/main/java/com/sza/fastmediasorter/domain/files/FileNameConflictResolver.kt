package com.sza.fastmediasorter.domain.files

import com.sza.fastmediasorter.util.CaptureFileNamer
import java.io.File

/**
 * Resolves filename collisions inside a target directory with the CAPTURE-OUTPUT rule 5 ordinal:
 * `name.txt` -> `name (2).txt` -> `name (3).txt`, never overwriting an existing file.
 *
 * Shared module owned by S0189 (Phase 09). Content-type agnostic - no extension or
 * filename pattern assumptions. Consumed by text notes (S0189) and drawings (S0191).
 */
object FileNameConflictResolver {

    /**
     * Returns the first name derived from [intendedName] that does not exist in [parentDir].
     *
     * @return pair of (finalName, wasRenamed). `wasRenamed = true` means an ordinal was applied.
     */
    fun resolveLocal(parentDir: File, intendedName: String): Pair<String, Boolean> {
        val finalName = CaptureFileNamer.freeNameIn(parentDir, intendedName)
        return Pair(finalName, finalName != intendedName)
    }
}
