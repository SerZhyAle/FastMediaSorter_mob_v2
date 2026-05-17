package com.sza.fastmediasorter.data.local

import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory registry of pending staged text notes for network/cloud resources.
 *
 * A note is registered here when [TextNoteStagingDirectory] creates the local staging file
 * (Phase 03) and unregistered when the save flow (Phase 06) successfully uploads it to the
 * remote resource.  If the upload fails the entry stays registered so the user can retry.
 *
 * Thread-safe via [ConcurrentHashMap].
 */
@Singleton
class TextNoteStagingRegistry @Inject constructor() {

    data class StagedNote(
        val localFile: File,
        val targetResourceId: Long,
        val targetParentPath: String,
        val intendedName: String
    )

    private val registry = ConcurrentHashMap<String, StagedNote>()

    fun register(
        file: File,
        targetResourceId: Long,
        targetParentPath: String,
        intendedName: String
    ) {
        val entry = StagedNote(file, targetResourceId, targetParentPath, intendedName)
        registry[file.absolutePath] = entry
        Timber.d("S0189: TextNoteStagingRegistry.register $file -> resource=$targetResourceId path=$targetParentPath")
    }

    fun unregister(file: File): StagedNote? =
        registry.remove(file.absolutePath)

    fun lookup(file: File): StagedNote? =
        registry[file.absolutePath]

    /** Returns a snapshot of all currently staged notes (for diagnostics). */
    fun snapshot(): List<StagedNote> = registry.values.toList()
}
