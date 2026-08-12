package com.sza.fastmediasorter.core.util

import com.sza.fastmediasorter.data.common.MediaTypeUtils
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.MetadataState
import timber.log.Timber

/**
 * Java-interop scanners can surface platform-typed nulls at runtime, so every
 * MediaFile creation path that crosses SMBJ/JSch/Commons-Net should sanitize
 * mandatory fields before constructing the non-null Kotlin model.
 */
object MediaFileIntegrity {

    data class Fields(
        val name: String,
        val path: String,
        val type: MediaType,
        val metadataState: MetadataState
    )

    fun sanitize(
        name: String?,
        path: String?,
        type: MediaType?,
        sourcePath: String?,
        metadataState: MetadataState? = MetadataState.COMPLETE
    ): Fields {
        val safeSourcePath = sourcePath?.takeIf { it.isNotBlank() }
            ?: path?.takeIf { it.isNotBlank() }
            ?: name?.takeIf { it.isNotBlank() }
            ?: "<unknown>"

        val safePath = path ?: sourcePath ?: run {
            logSubstitution("path", safeSourcePath)
            ""
        }

        val safeName = name ?: run {
            logSubstitution("name", safeSourcePath)
            safePath.substringAfterLast('/').ifBlank { "" }
        }

        val fallbackTypeToken = safeName.ifBlank {
            safePath.substringAfterLast('/')
        }
        val safeType = type ?: run {
            logSubstitution("type", safeSourcePath)
            MediaTypeUtils.getMediaType(fallbackTypeToken) ?: MediaType.TEXT
        }

        val safeMetadataState = metadataState ?: run {
            logSubstitution("metadataState", safeSourcePath)
            MetadataState.COMPLETE
        }

        return Fields(
            name = safeName,
            path = safePath,
            type = safeType,
            metadataState = safeMetadataState
        )
    }

    private fun logSubstitution(fieldName: String, sourcePath: String) {
        Timber.w("MediaFileIntegrity: substituted null %s for %s", fieldName, sourcePath)
    }
}