package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.security.fdsec.FdSecContainer
import com.sza.fastmediasorter.data.security.fdsec.FdSecCredentialRepository
import com.sza.fastmediasorter.data.security.fdsec.FdSecOutcome
import com.sza.fastmediasorter.domain.model.FdSecResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opens a FileDO `.fd-sec` container, in the two shapes the app needs: restoring the original
 * beside the container, and materialising it into a caller-owned directory for viewing.
 *
 * Both go through one read, because a read costs a full key derivation and a full verification pass
 * and the contract asks for the credential once per operation, not once per question about it.
 */
@Singleton
class UnsecureFdSecFileUseCase @Inject constructor(
    private val rememberedCredential: FdSecCredentialRepository,
) {

    /** Restores the original next to the container, under the name sealed inside it. */
    suspend fun restoreBeside(container: File, credential: CharArray): FdSecResult =
        withContext(Dispatchers.IO) {
            val parent = container.parentFile ?: return@withContext FdSecResult.Failed("the container has no folder")
            val staging = File(parent, STAGING_PREFIX + System.nanoTime())
            if (!staging.mkdirs()) {
                return@withContext FdSecResult.Failed("cannot create a working folder beside the container")
            }
            try {
                moveOutOfStaging(FdSecContainer().unpack(container, staging, credential), parent)
            } finally {
                staging.deleteRecursively()
            }
        }

    /**
     * Restores into [targetDirectory], which the caller owns and empties. Used for the view-only
     * path, where the recovered copy never leaves the app's private cache.
     */
    suspend fun materialize(container: File, targetDirectory: File, credential: CharArray): FdSecResult =
        withContext(Dispatchers.IO) {
            if (!targetDirectory.isDirectory && !targetDirectory.mkdirs()) {
                return@withContext FdSecResult.Failed("cannot create the working folder")
            }
            FdSecResultMapper.toResult(FdSecContainer().unpack(container, targetDirectory, credential))
        }

    /**
     * S3397: tries the remembered credential, if there is one. Returns null when nothing is
     * remembered. A refusal forgets it at once - the format cannot tell a wrong credential from a
     * foreign or tampered file, so "does not fit" is the only signal there is.
     */
    suspend fun materializeWithRemembered(container: File, targetDirectory: File): FdSecResult? {
        val credential = withContext(Dispatchers.IO) { rememberedCredential.read() } ?: return null
        val outcome = try {
            materialize(container, targetDirectory, credential)
        } finally {
            credential.fill('\u0000')
        }
        if (outcome is FdSecResult.WrongCredentialOrTamper) {
            withContext(Dispatchers.IO) { rememberedCredential.forget() }
        }
        return outcome
    }

    /** Called only after the credential has opened a container, so a typo is never kept. */
    suspend fun rememberCredential(credential: CharArray) {
        withContext(Dispatchers.IO) { rememberedCredential.save(credential) }
    }

    /** Sweeps staging folders a killed restore left behind. */
    fun sweepStaging(directory: File) {
        directory.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith(STAGING_PREFIX) }
            ?.forEach { it.deleteRecursively() }
    }

    private fun moveOutOfStaging(outcome: FdSecOutcome, parent: File): FdSecResult {
        if (outcome !is FdSecOutcome.Unpacked) {
            return FdSecResultMapper.toResult(outcome)
        }
        val target = freeTarget(parent, outcome.restored.name)
        return if (outcome.restored.renameTo(target)) {
            FdSecResultMapper.toResult(FdSecOutcome.Unpacked(target, outcome.metadata))
        } else {
            FdSecResult.Failed("cannot move the restored file out of the working folder")
        }
    }

    /** A collision is suffixed, never overwritten - the contract forbids clobbering silently. */
    private fun freeTarget(parent: File, name: String): File {
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

    private companion object {
        const val STAGING_PREFIX = ".fdsec-restore-"
    }
}
