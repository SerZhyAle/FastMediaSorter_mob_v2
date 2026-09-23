package com.sza.fastmediasorter.data.security.fdsec

import java.io.File

/**
 * The four FILETIME stamps a container seals. Zero means unknown and must round-trip as unknown
 * rather than as 1601, so the two stamps Android cannot report are left at zero on purpose.
 */
data class FdSecStamps(
    val encryptedAtFileTime: Long,
    val createdFileTime: Long,
    val accessedFileTime: Long,
    val modifiedFileTime: Long,
) {
    companion object {
        fun of(source: File, nowMillis: Long = System.currentTimeMillis()): FdSecStamps = FdSecStamps(
            encryptedAtFileTime = FdSecMetadata.millisToFileTime(nowMillis),
            createdFileTime = 0L,
            accessedFileTime = 0L,
            modifiedFileTime = FdSecMetadata.millisToFileTime(source.lastModified()),
        )
    }
}
