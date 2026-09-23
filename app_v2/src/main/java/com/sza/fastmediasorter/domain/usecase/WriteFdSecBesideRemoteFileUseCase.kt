package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.data.transfer.SiblingFolderResolver
import com.sza.fastmediasorter.domain.model.FdSecResult
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.transfer.SiblingFolder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * S3408: the FileDO menu pair for a file on a document tree, a network share or (S3409) a cloud drive.
 *
 * The container format reads and writes `java.io.File` only, so each operation works on a private
 * copy: the source is fetched into a staging directory in the app cache, the existing local operation
 * runs there with its own read-back proof, and only the proven result travels back beside the source
 * through [PlaceVerifiedFileBesideUseCase]. The source itself is never modified. The staging directory
 * holds the plaintext during a decrypt and goes at the end of every operation.
 */
class WriteFdSecBesideRemoteFileUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val localize: LocalizeFdSecContainerUseCase,
    private val secureFile: SecureFileToFdSecUseCase,
    private val unsecureFile: UnsecureFdSecFileUseCase,
    private val siblingFolders: SiblingFolderResolver,
    private val place: PlaceVerifiedFileBesideUseCase,
) {

    /** Packs [file] into a container beside it. [currentFolder] is the folder the list is showing. */
    suspend fun encrypt(file: MediaFile, currentFolder: String?, credential: CharArray): FdSecResult =
        withStaging(file, currentFolder) { folder, staging ->
            Timber.d("S3408: encrypt beside a remote file entered")
            val original = localize.copyOf(file.path, File(staging, SOURCE_DIRECTORY), file.name)
            if (original == null) {
                FdSecResult.Failed("the file could not be read where it is")
            } else {
                // The container seals the original's modification time; the copy's own is the fetch time.
                val modified = file.lastModified.takeIf { it > 0L } ?: file.createdDate
                if (modified > 0L) original.setLastModified(modified)
                val packed = secureFile(original, credential)
                if (packed is FdSecResult.Packed) {
                    place(packed.container, folder, packed.container.name, staging)
                } else {
                    packed
                }
            }
        }

    /** Restores the original under its sealed name beside the container [file]. */
    suspend fun decrypt(file: MediaFile, currentFolder: String?, credential: CharArray): FdSecResult =
        withStaging(file, currentFolder) { folder, staging ->
            Timber.d("S3408: decrypt beside a remote file entered")
            val container = localize(file.path, File(staging, SOURCE_DIRECTORY))
            val restored = container?.let {
                unsecureFile.materialize(it, File(staging, RESTORED_DIRECTORY), credential)
            }
            when (restored) {
                null -> FdSecResult.Failed("the container could not be read where it is")
                is FdSecResult.Restored -> place(restored.file, folder, restored.file.name, staging)
                else -> restored
            }
        }

    /** Sweeps staging directories a killed operation left behind, plaintext included. */
    fun sweepStaging() {
        stagingRoot().listFiles()?.forEach { it.deleteRecursively() }
    }

    private suspend fun withStaging(
        file: MediaFile,
        currentFolder: String?,
        operation: suspend (SiblingFolder, File) -> FdSecResult,
    ): FdSecResult {
        // Off the main thread: naming a document's folder is a call into its provider.
        val folder = withContext(Dispatchers.IO) { siblingFolders.folderBeside(file.path, currentFolder) }
            ?: return FdSecResult.Failed("this location cannot be written from here")
        val staging = withContext(Dispatchers.IO) {
            File(stagingRoot(), System.nanoTime().toString()).takeIf { it.mkdirs() }
        }
        return if (staging == null) {
            FdSecResult.Failed("cannot create a working folder in the app cache")
        } else {
            try {
                // The operation stages files itself (a folder, a timestamp), so it never runs on the caller's Main.
                withContext(Dispatchers.IO) { operation(folder, staging) }
            } finally {
                withContext(Dispatchers.IO) { staging.deleteRecursively() }
            }
        }
    }

    // Canonical: /data/user/0 is a link to /data/data, and the packer refuses a source whose canonical
    // path differs from its absolute one ("a link is not packed").
    private fun stagingRoot(): File = File(context.cacheDir.canonicalFile, STAGING_DIRECTORY)

    companion object {
        private const val STAGING_DIRECTORY = "fdsec-place"
        private const val SOURCE_DIRECTORY = "source"
        private const val RESTORED_DIRECTORY = "restored"
        private val REMOTE_PREFIXES = listOf("content:", "smb://", "sftp://", "ftp://", "cloud://")

        /**
         * Where the pair can write beside a file: a local path through `java.io.File`; a document tree,
         * a network share or a cloud drive through this use case.
         */
        fun canWriteBeside(path: String): Boolean =
            path.startsWith("/") || REMOTE_PREFIXES.any { path.startsWith(it, ignoreCase = true) }
    }
}
