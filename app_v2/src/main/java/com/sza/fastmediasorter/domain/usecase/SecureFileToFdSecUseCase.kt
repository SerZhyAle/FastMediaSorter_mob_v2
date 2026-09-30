package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.security.fdsec.FdSecContainer
import com.sza.fastmediasorter.data.security.fdsec.FdSecFormat
import com.sza.fastmediasorter.domain.model.FdSecResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Packs one file into a FileDO `.fd-sec` container beside it.
 *
 * The original is always kept: of the FDSEC-BEHAVIOUR section 9 dispositions this product implements
 * only the default. `del` leaves the bytes recoverable and `wipe` cannot promise erasure on flash
 * storage, so either one arrives as its own user-facing feature with its caveat, never as a helper here.
 */
@Singleton
class SecureFileToFdSecUseCase @Inject constructor() {

    suspend operator fun invoke(source: File, credential: CharArray): FdSecResult =
        withContext(Dispatchers.IO) {
            val refusal = refuse(source)
            refusal ?: FdSecResultMapper.toResult(FdSecContainer().pack(source, freeDestination(source), credential))
        }

    fun isContainer(path: String): Boolean = path.endsWith(FdSecFormat.CONTAINER_SUFFIX, ignoreCase = true)

    private fun refuse(source: File): FdSecResult? = when {
        !source.isFile -> FdSecResult.Failed("only a regular file can be encrypted")
        isReparsePoint(source) -> FdSecResult.Failed("a link is not packed, only a regular file")
        isContainer(source.name) -> FdSecResult.Failed("this file is already a container")
        else -> null
    }

    private fun isReparsePoint(source: File): Boolean = source.canonicalFile != source.absoluteFile

    /**
     * The container's name is the original's with its extension dropped, so the visible name does
     * not say what kind of file is inside. A collision is suffixed, never overwritten.
     */
    private fun freeDestination(source: File): File {
        val stem = source.nameWithoutExtension
        val parent = source.parentFile
        var candidate = File(parent, stem + FdSecFormat.CONTAINER_SUFFIX)
        var ordinal = 1
        while (candidate.exists()) {
            candidate = File(parent, "$stem-$ordinal${FdSecFormat.CONTAINER_SUFFIX}")
            ordinal++
        }
        return candidate
    }
}
