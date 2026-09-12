package com.sza.fastmediasorter.domain.identity.transfer

/**
 * Sign-in state that is meant to survive a migration to a new device (S2101).
 *
 * Provider-neutral by construction: an entry names its provider with a plain string and carries an
 * opaque payload, so a fourth provider is added without touching [TransferableSignInStore] or this
 * type. The values in [TransferableSignInProviderKeys] and the [Kind] names are part of the
 * persisted format - a build that reads this record may be older or newer than the one that wrote
 * it, so neither may be renamed once shipped.
 */
data class TransferableSignInRecord(
    val schemaVersion: Int,
    val writtenAt: Long,
    val entries: List<Entry>
) {

    /**
     * One provider's transferable state.
     *
     * @param providerKey a value from [TransferableSignInProviderKeys].
     * @param kind whether [payload] carries a secret; see [Kind].
     * @param payload provider-specific fields, interpreted only by the code that wrote them.
     */
    data class Entry(
        val providerKey: String,
        val kind: Kind,
        val payload: Map<String, String>
    )

    /**
     * What an entry's payload is, which decides how much damage a leak of it would do.
     *
     * [IDENTITY_ENVELOPE] carries no secret - which account was chosen and which scopes were
     * granted - and the new device issues tokens afresh against the system account that migrated on
     * its own (ADR-2). [SECRET] carries a refresh token, and exists only for the routes that store
     * one in app-owned storage, permitted by owner decision 2.
     */
    enum class Kind {
        IDENTITY_ENVELOPE,
        SECRET
    }

    /** Returns this record with [entry] replacing any entry under the same provider key. */
    fun withEntry(entry: Entry): TransferableSignInRecord =
        copy(entries = entries.filterNot { it.providerKey == entry.providerKey } + entry)

    /** Returns this record without the entry under [providerKey], if any. */
    fun withoutProvider(providerKey: String): TransferableSignInRecord =
        copy(entries = entries.filterNot { it.providerKey == providerKey })

    companion object {
        /**
         * Bumped whenever the meaning of an existing field changes. Adding a field does not require
         * a bump: the codec drops what it does not recognise and keeps the rest.
         */
        const val SCHEMA_VERSION: Int = 1

        /** An empty record stamped at [writtenAt], the starting point for the first write. */
        fun empty(writtenAt: Long): TransferableSignInRecord =
            TransferableSignInRecord(SCHEMA_VERSION, writtenAt, emptyList())
    }
}
