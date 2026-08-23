package com.sza.fastmediasorter.domain.model

/**
 * S1019: single source of truth for "may the user perform write operations (move / rename / delete /
 * upload / save) on this resource". Every UI affordance (player + browse) and every operation-boundary
 * guard resolves write permission through this, so what the user sees offered always matches what the
 * operation layer will attempt - previously the player and browse derived it independently and diverged.
 *
 * [MediaResource.isReadOnly] is the authoritative user policy and always wins. Beyond it:
 * - network resources (SMB/SFTP/FTP): writable when reachable. The `isWritable` probe for these is a
 *   connectivity check, not a per-folder permission check, so it cannot pre-decide writability; the
 *   server enforces at operation time and a rejected write surfaces its own error, instead of the UI
 *   hiding actions the user explicitly enabled by clearing "read-only".
 * - LOCAL / CLOUD: gated by the probed [MediaResource.isWritable], which is an accurate
 *   filesystem / provider writability check for those types.
 * - streams (HTTP / RTSP): never writable.
 * - the paired watch (S1861): writable whenever the bridge is up. Like the network types, the probe
 *   behind [MediaResource.isWritable] is a reachability check and not a per-folder permission check,
 *   so it cannot pre-decide writability; the send itself reports what actually happened.
 */
fun MediaResource.allowsWriteOperations(): Boolean {
    if (isReadOnly) return false
    return when (type) {
        ResourceType.SMB, ResourceType.SFTP, ResourceType.FTP -> true
        ResourceType.LOCAL, ResourceType.CLOUD -> isWritable
        ResourceType.WEAR_WATCH -> true
        ResourceType.HTTP_STREAM, ResourceType.RTSP_STREAM -> false
    }
}
