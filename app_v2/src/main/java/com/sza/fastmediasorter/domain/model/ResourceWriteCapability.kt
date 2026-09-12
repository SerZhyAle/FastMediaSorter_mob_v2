package com.sza.fastmediasorter.domain.model

/**
 * S1019: single source of truth for "may the user perform write operations (move / rename / delete /
 * upload / save / create folder) on this resource". S2594 named folder creation explicitly: it is a
 * write into the resource tree of the same class as upload, and leaving it unlisted is what kept the
 * browse resource affordances on a hand-rolled predicate.
 * S2625 replaced a blanket claim that all UI affordances and all operation guards resolved through this
 * helper with the two lists below: the claim was checked and was false on its second half, where four
 * boundaries read [MediaResource.isReadOnly] alone. A promise about every caller cannot be verified by
 * reading this file, so it is stated as an enumeration that can - keep it that way, and keep it current.
 *
 * Callers resolving through this helper: folder creation, text note creation, drawing creation,
 * Quick Sort destination assignment, the player's prepare-for-write path, and - since S2646 - the
 * browse affordance policies for text note, drawing and capture destination plus the
 * destination-list filter.
 *
 * Still reading [MediaResource.isReadOnly] alone: none. S2646 emptied this list; keep the line
 * rather than deleting it, because an empty list that is kept current is checkable by reading this
 * file, and an absent one is indistinguishable from one nobody maintains.
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
