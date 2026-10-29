package com.sza.fastmediasorter.domain.model

/** S4247, LIVE-BROADCAST item R: how the password of a prepared link was chosen. */
enum class PreparedLinkChoice { GENERATED, CUSTOM, OPEN }

/**
 * S4247, DEVICE-EXCHANGE item DW: a broadcast prepared before its start - the id, the link pair and the keys a
 * pending descriptor hands out, kept until the start consumes them. A consumed record stays keyed by [broadcastId]
 * with its secrets cleared, so a later permanent channel can reuse the key.
 */
data class PreparedBroadcastLink(
    val broadcastId: String,
    val entryIdentity: String,
    val publicEndpoint: String,
    val certFingerprint: String?,
    val mode: String,
    val title: String,
    val sourceId: String?,
    val createdAtMs: Long,
    val untilMs: Long,
    val linkChoice: PreparedLinkChoice,
    val linkUser: String?,
    val linkPassword: String?,
    val relayLinkKey: String?,
    val tunnelAlias: String?,
    val tunnelPin: String?,
    val guests: List<String> = emptyList(),
    val consumed: Boolean = false,
) {

    fun isLive(nowMs: Long): Boolean = !consumed && untilMs > nowMs

    // The id is a capability and the rest are secrets: a log line carries only the id's first 8 characters (AF).
    override fun toString(): String =
        "PreparedBroadcastLink(id=${broadcastId.take(LOGGED_ID_CHARS)}.., mode=$mode, consumed=$consumed)"

    private companion object {
        const val LOGGED_ID_CHARS = 8
    }
}
