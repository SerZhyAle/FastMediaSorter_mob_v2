package com.sza.fastmediasorter.domain.path

import com.sza.fastmediasorter.domain.model.ResourceType

/**
 * Reduces a raw resource path (local, SMB/SFTP/FTP, cloud) to a canonical form so
 * equality comparison between Player-produced records and Browse cache entries is reliable.
 *
 * Contract:
 * - Idempotent: `canonical(canonical(x, t), t) == canonical(x, t)` for every input.
 * - Pure: no I/O, no side effects beyond log warnings.
 * - Never throws - degenerate input is returned trimmed.
 */
interface PathNormalizer {

    fun canonical(rawPath: String, resourceType: ResourceType): String
}
