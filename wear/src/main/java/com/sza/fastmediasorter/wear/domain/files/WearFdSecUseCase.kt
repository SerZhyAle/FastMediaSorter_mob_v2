package com.sza.fastmediasorter.wear.domain.files

import com.sza.fastmediasorter.data.security.fdsec.FdSecContainer
import com.sza.fastmediasorter.data.security.fdsec.FdSecFormat
import com.sza.fastmediasorter.data.security.fdsec.FdSecOutcome
import com.sza.fastmediasorter.wear.domain.model.WearFdSecResult
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3383: the watch's whole dealing with FileDO `.fd-sec` containers.
 *
 * Packing, restoring beside the container and materialising for viewing are three errands of one
 * class because they share every rule that matters: the original is never touched, a collision is
 * suffixed rather than overwritten, and each refusal class is reported as itself.
 *
 * One file at a time, always. The contract forbids bulk, recursive and scheduled packing outright,
 * so there is no batch entry point here for a caller to reach for.
 */
@Singleton
class WearFdSecUseCase @Inject constructor() {

    /** Writes the container beside [source], keeping [source] exactly where it is. */
    suspend fun pack(source: File, credential: CharArray): WearFdSecResult =
        withContext(Dispatchers.IO) {
            refuse(source) ?: guarded { FdSecContainer().pack(source, freeContainerName(source), credential) }
        }

    /** Restores the original next to [container], under the name sealed inside it. */
    suspend fun restoreBeside(container: File, credential: CharArray): WearFdSecResult =
        withContext(Dispatchers.IO) {
            val parent = container.parentFile
            if (parent == null) {
                WearFdSecResult.Failed("the container has no folder")
            } else {
                restoreInto(parent, container, credential)
            }
        }

    /**
     * Restores into [targetDirectory], which the caller owns and empties.
     *
     * The view-only path: the recovered copy never leaves the app's private cache, so this is the
     * one shape that hands a container's contents to a screen.
     */
    suspend fun materialize(container: File, targetDirectory: File, credential: CharArray): WearFdSecResult =
        withContext(Dispatchers.IO) {
            if (!targetDirectory.isDirectory && !targetDirectory.mkdirs()) {
                WearFdSecResult.Failed("cannot create the working folder")
            } else {
                guarded { FdSecContainer().unpack(container, targetDirectory, credential) }
            }
        }

    /**
     * Whether [name] addresses a container.
     *
     * The only place on the watch that answers it, so the browse list, the capability policy and the
     * credential screen cannot disagree about what a container is.
     */
    fun isContainer(name: String): Boolean = name.endsWith(FdSecFormat.CONTAINER_SUFFIX, ignoreCase = true)

    /**
     * The one FileDO operation a selection admits, or neither.
     *
     * A container can only be opened and anything else can only be packed, so the menu never shows
     * both and never packs a container twice. The switch is the wearer's answer, exactly one file is
     * the contract's rule - it forbids bulk, recursive and scheduled packing outright - and a file the
     * source may not write beside is offered neither.
     *
     * @param singleName the name of the one selected file, or null when the selection is not exactly one.
     * @param writable whether the source lets a new file be written beside the selected one.
     */
    fun offerFor(singleName: String?, enabled: Boolean, writable: Boolean): Set<WearFileOperationKind> = when {
        !enabled || singleName == null || !writable -> emptySet()
        isContainer(singleName) -> setOf(WearFileOperationKind.DECRYPT_FILEDO)
        else -> setOf(WearFileOperationKind.ENCRYPT_FILEDO)
    }

    /**
     * Whether a shared-storage folder, named by its MediaStore relative path, lets this app write a
     * container beside a file in it.
     *
     * Scoped storage admits a file with no media type only under the Download and Documents top-level
     * folders, and a container is exactly such a file. Anywhere else the platform refuses the write -
     * after the wearer has typed the credential twice - so the entry is withheld instead.
     */
    fun sharedFolderTakesContainer(relativePath: String?): Boolean =
        relativePath?.substringBefore('/') in NON_MEDIA_TOP_FOLDERS

    /** The private folder a container opened for viewing is recovered into, under [cacheRoot]. */
    fun openWorkspace(cacheRoot: File): File = File(cacheRoot, OPEN_WORKSPACE).apply { mkdirs() }

    /**
     * Deletes every copy recovered for viewing. Plaintext outlives its viewer by exactly as long as
     * nobody calls this, so it runs whenever a list is shown again and when a credential screen opens.
     */
    suspend fun discardOpened(cacheRoot: File) = withContext(Dispatchers.IO) {
        File(cacheRoot, OPEN_WORKSPACE).listFiles()?.forEach { it.deleteRecursively() }
        Unit
    }

    /** Sweeps the staging folders a killed restore left behind. */
    fun sweepStaging(directory: File) {
        directory.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith(STAGING_PREFIX) }
            ?.forEach { it.deleteRecursively() }
    }

    private fun restoreInto(parent: File, container: File, credential: CharArray): WearFdSecResult {
        val staging = File(parent, STAGING_PREFIX + System.nanoTime())
        if (!staging.mkdirs()) {
            return WearFdSecResult.Failed("cannot create a working folder beside the container")
        }
        return try {
            moveOutOfStaging(guarded { FdSecContainer().unpack(container, staging, credential) }, parent)
        } finally {
            staging.deleteRecursively()
        }
    }

    /**
     * The memory refusal is caught here rather than in the UI because this is the one boundary that
     * knows an `OutOfMemoryError` came from the key derivation.
     *
     * The format fixes a 64 MiB Argon2id profile and binds it to the format version, so no platform
     * may lower it: a lighter profile on the watch would write a container neither the phone nor
     * FileDO could open. Answering honestly is therefore the whole of what is done about it.
     */
    private inline fun guarded(block: () -> FdSecOutcome): WearFdSecResult = try {
        toResult(block())
    } catch (e: OutOfMemoryError) {
        Timber.w(e, "FD-SEC: the key derivation could not get its memory on this watch")
        WearFdSecResult.OutOfMemory
    }

    private fun moveOutOfStaging(result: WearFdSecResult, parent: File): WearFdSecResult {
        if (result !is WearFdSecResult.Restored) {
            return result
        }
        val target = freeTarget(parent, result.file.name)
        return if (result.file.renameTo(target)) {
            result.copy(file = target)
        } else {
            WearFdSecResult.Failed("cannot move the restored file out of the working folder")
        }
    }

    private fun refuse(source: File): WearFdSecResult? = when {
        !source.isFile -> WearFdSecResult.Failed("only a regular file can be encrypted")
        isReparsePoint(source) -> WearFdSecResult.Failed("a link is not packed, only a regular file")
        isContainer(source.name) -> WearFdSecResult.Failed("this file is already a container")
        else -> null
    }

    private fun isReparsePoint(source: File): Boolean = source.canonicalFile != source.absoluteFile

    /**
     * The container's name is the original's with its extension dropped, so the visible name does
     * not say what kind of file is inside.
     */
    private fun freeContainerName(source: File): File =
        freeTarget(source.parentFile, source.nameWithoutExtension + FdSecFormat.CONTAINER_SUFFIX)

    /** A collision is suffixed, never overwritten - the contract forbids clobbering silently. */
    private fun freeTarget(parent: File?, name: String): File {
        val stem = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "")
        val tail = if (extension.isEmpty()) "" else ".$extension"
        var candidate = File(parent, name)
        var ordinal = 1
        while (candidate.exists()) {
            candidate = File(parent, "$stem-$ordinal$tail")
            ordinal++
        }
        return candidate
    }

    /**
     * The one crossing from the container format's outcome type to what a watch screen may see.
     *
     * The true name travels with the result because the container's own visible name carries no
     * extension at all, so nothing above this point could resolve what was recovered.
     */
    private fun toResult(outcome: FdSecOutcome): WearFdSecResult = when (outcome) {
        is FdSecOutcome.Packed -> WearFdSecResult.Packed(outcome.container)
        is FdSecOutcome.Unpacked -> WearFdSecResult.Restored(
            file = outcome.restored,
            realName = outcome.metadata.originalName,
            realSize = outcome.metadata.realSize
        )
        is FdSecOutcome.WrongCredentialOrTamper -> WearFdSecResult.WrongCredentialOrTamper
        is FdSecOutcome.Damaged -> WearFdSecResult.Damaged
        is FdSecOutcome.Unsupported -> WearFdSecResult.Unsupported
        is FdSecOutcome.Failed -> WearFdSecResult.Failed(outcome.detail)
    }

    private companion object {
        const val STAGING_PREFIX = ".fdsec-restore-"
        const val OPEN_WORKSPACE = "fdsec-open"

        // The values of Environment.DIRECTORY_DOWNLOADS and DIRECTORY_DOCUMENTS, spelled out: those two
        // are non-final static fields, which read as null on the JVM the unit tests run on.
        val NON_MEDIA_TOP_FOLDERS = setOf("Download", "Documents")
    }
}
