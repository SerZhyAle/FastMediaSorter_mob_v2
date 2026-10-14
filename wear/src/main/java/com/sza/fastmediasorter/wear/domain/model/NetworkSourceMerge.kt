package com.sza.fastmediasorter.wear.domain.model

/**
 * S1734: decides which stored source an incoming one replaces.
 *
 * The rule used to live inline in the repository and matched on `type + server + port + shareName`,
 * which is blind to `basePath` - the only field separating two phone resources that point at
 * different folders of one share. Fifteen pushed sources became ten stored ones, silently.
 *
 * Worse than the loss was that it never converged: the import counted `added` / `updated` by the
 * phone-side id while the merge matched by the share tuple and then discarded the incoming id, so a
 * collapsed pair reported itself as newly added on every later sync. Identity is one thing here now.
 */
object NetworkSourceMerge {

    /**
     * Index of the stored source [incoming] replaces, or -1 when it is genuinely new.
     *
     * Matched by id first: the phone ships its own resource id and the import stores it, so an id
     * hit is the exact answer. The tuple below is the fallback for a source the watch created
     * itself, which no phone id can name - and it carries `basePath`, without which the fallback
     * would reproduce the defect this object exists to remove.
     */
    fun indexOfMatch(stored: List<NetworkSource>, incoming: NetworkSource): Int {
        val byId = stored.indexOfFirst { it.id == incoming.id }
        val byIdentity = if (byId == -1) indexOfIdentity(stored, incoming) else byId
        if (byIdentity != -1) {
            return byIdentity
        }
        return stored.indexOfFirst { existing ->
            existing.type == incoming.type &&
                existing.server == incoming.server &&
                existing.port == incoming.port &&
                existing.shareName == incoming.shareName &&
                existing.basePath == incoming.basePath
        }
    }

    /**
     * S4228: one SFTP folder is one record whatever channel brought it (contract DEVICE-EXCHANGE 20 item BA) - the
     * phone's push under the phone's id and the account catalog under one this watch made. Without a host-key
     * fingerprint there is no identity to compare, so such a record falls through to the tuple.
     */
    private fun indexOfIdentity(stored: List<NetworkSource>, incoming: NetworkSource): Int {
        val fingerprint = incoming.hostKeyFingerprint
        if (incoming.type != NetworkSourceType.SFTP || fingerprint.isNullOrBlank()) return -1
        return stored.indexOfFirst { existing ->
            existing.type == NetworkSourceType.SFTP &&
                existing.hostKeyFingerprint == fingerprint &&
                existing.identityRoot == incoming.identityRoot
        }
    }
}
