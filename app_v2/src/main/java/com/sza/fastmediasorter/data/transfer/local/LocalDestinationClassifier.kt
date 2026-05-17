package com.sza.fastmediasorter.data.transfer.local

import android.os.Environment
import android.webkit.MimeTypeMap
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationCategory.NonPublic
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationCategory.PublicCollection
import java.io.File
import javax.inject.Inject

/**
 * Classifies an absolute local filesystem path into a [LocalDestinationCategory].
 *
 * The first path segment under the external storage root is inspected against
 * the canonical `Environment.DIRECTORY_*` constants. Anything that matches
 * a media collection becomes a [PublicCollection] (with the appropriate
 * `Kind`); everything else — including paths outside external storage —
 * becomes a [NonPublic].
 *
 * Pure path arithmetic + `MimeTypeMap`. No `Context` injected.
 *
 * See strategic spec S0231 §6.1 (boundary rule) and §6.3 (MIME resolution).
 */
open class LocalDestinationClassifier @Inject constructor() {

    open fun classify(absolutePath: String): LocalDestinationCategory {
        val file = File(absolutePath)
        val displayName = file.name
        val mimeType = resolveMimeType(displayName)

        val externalRoot = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
        val normalizedExternal = externalRoot?.absolutePath?.trimEnd('/')
        val normalizedInput = absolutePath.trimEnd('/')

        if (normalizedExternal == null || !normalizedInput.startsWith("$normalizedExternal/")) {
            return NonPublic(
                absolutePath = absolutePath,
                displayName = displayName,
                mimeType = mimeType
            )
        }

        val relativeToRoot = normalizedInput.substring(normalizedExternal.length + 1)
        val segments = relativeToRoot.split('/').filter { it.isNotEmpty() }
        if (segments.size < 2) {
            // Bare external root or file directly in root — treat as non-public.
            return NonPublic(
                absolutePath = absolutePath,
                displayName = displayName,
                mimeType = mimeType
            )
        }

        val firstSegment = segments.first()
        val kind = matchPublicCollectionKind(firstSegment)
            ?: return NonPublic(
                absolutePath = absolutePath,
                displayName = displayName,
                mimeType = mimeType
            )

        // RELATIVE_PATH is everything except the last segment (filename), with trailing slash.
        val relativePath = segments.dropLast(1).joinToString(separator = "/", postfix = "/")

        return PublicCollection(
            collection = kind,
            relativePath = relativePath,
            displayName = displayName,
            mimeType = mimeType
        )
    }

    private fun matchPublicCollectionKind(segment: String): PublicCollection.Kind? = when (segment) {
        Environment.DIRECTORY_MUSIC,
        Environment.DIRECTORY_PODCASTS,
        Environment.DIRECTORY_AUDIOBOOKS,
        Environment.DIRECTORY_RINGTONES,
        Environment.DIRECTORY_NOTIFICATIONS,
        Environment.DIRECTORY_ALARMS -> PublicCollection.Kind.AUDIO
        Environment.DIRECTORY_MOVIES -> PublicCollection.Kind.VIDEO
        Environment.DIRECTORY_PICTURES,
        Environment.DIRECTORY_DCIM -> PublicCollection.Kind.IMAGES
        Environment.DIRECTORY_DOWNLOADS -> PublicCollection.Kind.DOWNLOADS
        else -> null
    }

    private fun resolveMimeType(displayName: String): String {
        val extension = displayName.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
        if (extension.isEmpty()) return DEFAULT_MIME
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: DEFAULT_MIME
    }

    private companion object {
        const val DEFAULT_MIME = "application/octet-stream"
    }
}
