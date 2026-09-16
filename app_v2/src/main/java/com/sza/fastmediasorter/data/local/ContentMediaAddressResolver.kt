package com.sza.fastmediasorter.data.local

import androidx.core.net.toUri
import com.sza.fastmediasorter.data.transfer.access.LocalFileAccess
import com.sza.fastmediasorter.domain.port.MediaAddressResolver
import javax.inject.Inject

/**
 * S3161: the platform answer for [MediaAddressResolver], delegated rather than re-implemented.
 *
 * `LocalFileAccess` already decides existence for both a content address and a plain path, and a
 * second spelling of that decision would drift from the one the transfer pipeline trusts.
 */
class ContentMediaAddressResolver @Inject constructor(
    private val localFileAccess: LocalFileAccess
) : MediaAddressResolver {

    override suspend fun exists(uri: String): Boolean = localFileAccess.exists(uri.toUri())
}
