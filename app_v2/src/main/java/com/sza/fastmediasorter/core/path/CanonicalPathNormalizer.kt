package com.sza.fastmediasorter.core.path

import android.net.Uri
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.path.PathNormalizer
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [PathNormalizer] used across the app.
 *
 * Per-type rules:
 * - `LOCAL`: `content://` -> `content://<authority><path>` (query+fragment dropped; the authority
 *   is part of the identity, S3770); otherwise pure string resolution of `.`/`..`
 *   segments without touching the filesystem. Trailing slash stripped unless the input is root.
 * - `SMB` / `SFTP` / `FTP`: lowercase host, URL-decoded segments, collapsed double slashes,
 *   scheme stripped.
 * - `CLOUD` dispatches by raw-path prefix to one of GOOGLE_DRIVE / DROPBOX / ONE_DRIVE
 *   sub-rules - there is no per-provider `ResourceType` in this project (provider lives on
 *   `MediaResource.cloudProvider`); the prefix is the only signal available at this layer.
 *
 * Never throws - degenerate input is returned trimmed after a [Timber.w].
 */
@Singleton
class CanonicalPathNormalizer @Inject constructor() : PathNormalizer {

    override fun canonical(rawPath: String, resourceType: ResourceType): String {
        val trimmed = rawPath.trim()
        if (trimmed.isEmpty()) return trimmed
        return try {
            when (resourceType) {
                ResourceType.LOCAL -> canonicalizeLocal(trimmed)
                ResourceType.SMB -> canonicalizeNetwork(trimmed, SCHEME_SMB)
                ResourceType.SFTP -> canonicalizeNetwork(trimmed, SCHEME_SFTP)
                ResourceType.FTP -> canonicalizeNetwork(trimmed, SCHEME_FTP)
                ResourceType.CLOUD -> canonicalizeCloud(trimmed)
                ResourceType.HTTP_STREAM, ResourceType.RTSP_STREAM -> trimmed
                // S1861: a watch path names a location in the watch's own storage, so resolving it
                // against this device's filesystem would produce a path that exists on neither.
                ResourceType.WEAR_WATCH -> trimmed
            }
        } catch (t: Throwable) {
            Timber.w(t, "PathNormalizer: failed to canonicalize %s for %s", rawPath, resourceType)
            trimmed
        }
    }

    private fun canonicalizeLocal(raw: String): String {
        if (raw.startsWith(SCHEME_CONTENT)) {
            Timber.d("S3770: canonicalizeLocal content uri entered - raw=$raw")
            // S3770: the authority is part of the identity - two providers can expose the same path
            // (content://a/root/x vs content://b/root/x), and a path-only form made the reconciler
            // match the wrong row. Query+fragment stay dropped, the path stays decoded (Uri.path),
            // so the form is idempotent under re-canonicalization; keeping the scheme also keeps
            // this branch collision-free with every other one. File.canonicalPath would resolve
            // against cwd and break the identity.
            val uri = Uri.parse(raw)
            val pathPart = uri.path ?: return raw
            val authority = uri.authority ?: return stripTrailingSlash(pathPart)
            return SCHEME_CONTENT + authority + stripTrailingSlash(pathPart)
        }
        // S3769: pure string resolution instead of File.canonicalPath, which touches disk
        // and violates the PathNormalizer no-IO contract.
        return stripTrailingSlash(resolveSegments(raw))
    }

    /**
     * Resolves `.` and `..` segments in a POSIX-style absolute path purely by string
     * manipulation. Collapses double slashes. Does NOT touch the filesystem (no symlink
     * resolution) - the tradeoff is intentional: the normalizer runs on the main thread
     * inside [BrowseReconcilerManager.reconcile] once per visible file, and correctness
     * for equality comparison does not require symlink resolution.
     */
    private fun resolveSegments(path: String): String {
        val segments = path.split('/')
        val resolved = ArrayList<String>(segments.size)
        for (segment in segments) {
            when (segment) {
                "", "." -> {
                    // Empty segments come from leading/double slashes; "." is current dir.
                    // Keep the very first empty segment (leading slash marker).
                    if (resolved.isEmpty()) resolved += segment
                }
                ".." -> {
                    // Pop the last real segment; never pop past root.
                    if (resolved.size > 1) resolved.removeAt(resolved.lastIndex)
                }
                else -> resolved += segment
            }
        }
        // Single empty segment = root "/".
        return if (resolved.size == 1 && resolved[0].isEmpty()) "/" else resolved.joinToString("/")
    }

    private fun canonicalizeNetwork(raw: String, scheme: String): String {
        val noScheme = if (raw.startsWith(scheme, ignoreCase = true)) {
            raw.substring(scheme.length)
        } else {
            raw
        }
        val collapsed = noScheme.replace(MULTI_SLASH_REGEX, "/")
        val firstSlash = collapsed.indexOf('/')
        val hostPart: String
        val pathPart: String
        if (firstSlash < 0) {
            hostPart = collapsed
            pathPart = ""
        } else {
            hostPart = collapsed.substring(0, firstSlash)
            pathPart = collapsed.substring(firstSlash)
        }
        val decodedPath = pathPart.split('/').joinToString("/") { segment ->
            if (segment.isEmpty()) segment else Uri.decode(segment) ?: segment
        }
        return scheme + hostPart.lowercase() + stripTrailingSlash(decodedPath)
    }

    private fun canonicalizeCloud(raw: String): String {
        return when {
            raw.startsWith(SCHEME_DROPBOX, ignoreCase = true) -> normalizeDropbox(raw.substring(SCHEME_DROPBOX.length))
            raw.startsWith(SCHEME_ONE_DRIVE, ignoreCase = true) -> normalizeOneDrive(raw.substring(SCHEME_ONE_DRIVE.length))
            raw.startsWith(SCHEME_GDRIVE_LONG, ignoreCase = true) -> normalizeGoogleDrive(raw.substring(SCHEME_GDRIVE_LONG.length))
            raw.startsWith(SCHEME_GDRIVE_SHORT, ignoreCase = true) -> normalizeGoogleDrive(raw.substring(SCHEME_GDRIVE_SHORT.length))
            raw.startsWith(SCHEME_DRIVE_GENERIC, ignoreCase = true) -> normalizeGoogleDrive(raw.substring(SCHEME_DRIVE_GENERIC.length))
            // Heuristic for bare cloud paths - case-insensitive path is the only safe choice.
            raw.startsWith('/') -> normalizeDropbox(raw)
            else -> normalizeGoogleDrive(raw)
        }
    }

    private fun normalizeGoogleDrive(raw: String): String {
        // Google Drive: the canonical identity is the file ID. If the input contains no
        // slashes treat it as an ID; otherwise return the trimmed, leading-slash-stripped
        // path (callers store IDs, not paths, so this is mostly identity).
        val trimmed = raw.trimStart('/')
        return trimmed
    }

    private fun normalizeDropbox(raw: String): String {
        // Dropbox is case-insensitive - lowercase the whole path, ensure leading '/'.
        val withLeadingSlash = if (raw.startsWith('/')) raw else "/$raw"
        return stripTrailingSlash(withLeadingSlash.lowercase())
    }

    private fun normalizeOneDrive(raw: String): String {
        // OneDrive Graph: path is case-insensitive; itemId is not a path so it bypasses
        // the leading-slash branch.
        return if (raw.startsWith('/')) {
            stripTrailingSlash(raw.lowercase())
        } else {
            raw
        }
    }

    private fun stripTrailingSlash(path: String): String {
        if (path.length <= 1) return path
        return if (path.endsWith('/')) path.substring(0, path.length - 1) else path
    }

    companion object {
        private const val SCHEME_CONTENT = "content://"
        private const val SCHEME_SMB = "smb://"
        private const val SCHEME_SFTP = "sftp://"
        private const val SCHEME_FTP = "ftp://"
        private const val SCHEME_DROPBOX = "dropbox://"
        private const val SCHEME_ONE_DRIVE = "onedrive://"
        private const val SCHEME_GDRIVE_LONG = "googledrive://"
        private const val SCHEME_GDRIVE_SHORT = "gdrive:/"
        private const val SCHEME_DRIVE_GENERIC = "drive://"
        private val MULTI_SLASH_REGEX = Regex("/{2,}")
    }
}
