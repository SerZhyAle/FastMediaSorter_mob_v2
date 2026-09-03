package com.sza.fastmediasorter.domain.identity.transfer

/**
 * Port for sign-in state that outlives the device it was created on (S2101).
 *
 * Three operations and nothing more: write on a successful sign-in, read once on the first launch
 * after a migration, erase on sign-out. The implementation is mounted per flavor - Block Store in
 * `cloudEnabled`, an inert stub in `cloudDisabled` - so no caller branches on flavor or on API
 * level.
 *
 * Every operation must be safe to call when the underlying mechanism is unavailable, returning the
 * failure value below rather than throwing: restoration is required to be silent, and a device that
 * cannot transfer must offer the ordinary sign-in instead of surfacing an error.
 */
interface TransferableSignInStore {

    /** Whether the platform mechanism can be used on this device at all. */
    suspend fun isAvailable(): Boolean

    /** Persists [record], replacing whatever was stored. Returns false when nothing was written. */
    suspend fun save(record: TransferableSignInRecord): Boolean

    /**
     * Reads the record left by the previous device, or null when there is none, when the record is
     * unreadable, or when the mechanism is unavailable.
     *
     * Named for its intended single use on the first launch after a migration. It is idempotent and
     * non-destructive, so calling it twice is harmless, but a second caller is a sign the restore
     * path has been duplicated rather than reused.
     */
    suspend fun readOnce(): TransferableSignInRecord?

    /** Erases the stored record. Safe to call when nothing is stored. */
    suspend fun clear()
}
