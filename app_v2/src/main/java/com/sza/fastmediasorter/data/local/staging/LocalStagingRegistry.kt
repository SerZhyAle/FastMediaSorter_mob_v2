package com.sza.fastmediasorter.data.local.staging

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0189 (Phase 09): in-memory registry of pending staged file creations.
 *
 * An entry is registered when an [com.sza.fastmediasorter.data.transfer.strategy.OperationStrategy]
 * creates the local staging file (deferred or otherwise) and is unregistered when the save
 * flow successfully commits it to the final destination (local or remote).  If commit fails
 * the entry stays registered so the user can retry.
 *
 * Two orthogonal dimensions discriminate entries:
 * - [Location] - *where* the bytes physically land (network-staged scratch dir vs
 *   local deferred final path).
 * - [StagedKind] - *what kind of content* (text note vs drawing); drives viewer routing
 *   in the deferred-bypass loader.
 *
 * Thread-safe via [ConcurrentHashMap].
 */
@Singleton
class LocalStagingRegistry @Inject constructor() {

    /**
     * Distinguishes how a staged file's bytes reach their final destination.
     *
     * - [NETWORK_STAGED]: `localFile` lives in `Downloads/FastMediaSorter/<kind-dir>/` and must
     *   be uploaded to `targetParentPath` (smb://, ftp://, sftp://, cloud://) on save.
     * - [LOCAL_DEFERRED]: `localFile` is the final target inside a LOCAL resource directory.
     *   No upload step; on save the file is written directly to this path.
     */
    enum class Location { NETWORK_STAGED, LOCAL_DEFERRED }

    data class StagedEntry(
        val localFile: File,
        val targetResourceId: Long,
        val targetParentPath: String,
        val intendedName: String,
        val location: Location,
        val kind: StagedKind,
    )

    private val registry = ConcurrentHashMap<String, StagedEntry>()

    fun register(
        file: File,
        targetResourceId: Long,
        targetParentPath: String,
        intendedName: String,
        kind: StagedKind,
        location: Location = Location.NETWORK_STAGED,
    ) {
        val entry = StagedEntry(file, targetResourceId, targetParentPath, intendedName, location, kind)
        registry[file.absolutePath] = entry
    }

    fun unregister(file: File): StagedEntry? =
        registry.remove(file.absolutePath)

    fun lookup(file: File): StagedEntry? =
        registry[file.absolutePath]

    /** Returns a snapshot of all currently staged entries (for diagnostics). */
    fun snapshot(): List<StagedEntry> = registry.values.toList()
}
