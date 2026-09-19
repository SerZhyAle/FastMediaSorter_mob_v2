package com.sza.fastmediasorter.domain.port

/**
 * S3161: answers whether an address this device stored actually resolves on this device.
 *
 * A port rather than a direct `ContentResolver` call so the maintenance that deletes rows by this
 * answer can be proven without an Android runtime: deleting a favourite the owner made is the exact
 * failure that prune must never commit, and that is only testable if the answer can be dictated.
 */
interface MediaAddressResolver {

    /** True when the device can still open [uri]; false when it resolves to nothing. */
    suspend fun exists(uri: String): Boolean
}
