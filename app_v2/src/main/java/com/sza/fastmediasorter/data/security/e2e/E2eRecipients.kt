package com.sza.fastmediasorter.data.security.e2e

/** What this device knows of another device of the entry, as the directory lists it. */
data class E2ePeer(
    val deviceId: String,
    /** `kem` of the published `e2eKey`; null when the record carries none. */
    val kem: String?,
    /** The record declares `e2e-v1`. */
    val capable: Boolean,
    val updatedAtMs: Long,
)

/** How a listed device stands for sealing (contract item DR). */
enum class E2ePeerState {
    /** Capable, keyed, and its key is the one this device trusts. */
    TRUSTED,

    /** No capability or no key: never a recipient (`e2e-missing`). */
    MISSING,

    /** Its key differs from the trusted one: sealed nothing until removed (`key-changed`). */
    KEY_CHANGED,
}

/**
 * The trust and recipient rules of contract item DG, shared by the phone and the watch so both pick the same
 * recipients from the same directory. Pure: the caller keeps the trusted keys and persists [learned].
 */
object E2eRecipients {

    fun state(peer: E2ePeer, trustedKem: String?): E2ePeerState = when {
        !peer.capable || peer.kem == null -> E2ePeerState.MISSING
        trustedKem != null && trustedKem != peer.kem -> E2ePeerState.KEY_CHANGED
        else -> E2ePeerState.TRUSTED
    }

    /** The keys seen for the first time - trust on first use - to add beside [trusted]; never a replacement. */
    fun learned(selfId: String, peers: List<E2ePeer>, trusted: Map<String, String>): Map<String, String> =
        peers.filter { it.deviceId != selfId && it.capable && it.kem != null && it.deviceId !in trusted }
            .associate { it.deviceId to requireNotNull(it.kem) }

    /**
     * The recipients of a box: listed devices other than this one whose state is trusted, the 40 seen most
     * recently when there are more (item DH). [trusted] must already hold the [learned] keys.
     */
    fun recipients(selfId: String, peers: List<E2ePeer>, trusted: Map<String, String>): List<E2eRecipient> =
        peers.asSequence()
            .filter { it.deviceId != selfId && state(it, trusted[it.deviceId]) == E2ePeerState.TRUSTED }
            .sortedByDescending { it.updatedAtMs }
            .take(E2eSealedBox.MAX_RECIPIENTS)
            .mapNotNull { peer -> peer.kem?.let(E2eDeviceKey::decodeKem)?.let { E2eRecipient(peer.deviceId, it) } }
            .toList()

    /** Whether [fromKey] is the key this device trusts for [from] - the opening check of item DH. */
    fun trusts(trusted: Map<String, String>, from: String, fromKey: ByteArray): Boolean =
        trusted[from]?.let(E2eDeviceKey::decodeKem)?.let { E2eBytes.constantTimeEquals(it, fromKey) } == true

    /** Canonical identity of a single recipient (`deviceId:hex(publicKey)`). */
    fun keyOf(recipient: E2eRecipient): String = "${recipient.deviceId}:${E2eBytes.hex(recipient.publicKey)}"

    /**
     * Canonical order-independent identity of [recipients], sorted deterministically by `(deviceId, hex(publicKey))`.
     */
    fun keyOf(recipients: Collection<E2eRecipient>): String =
        recipients.map { it.deviceId to E2eBytes.hex(it.publicKey) }
            .sortedWith(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })
            .joinToString(",") { "${it.first}:${it.second}" }

    /** Canonical identity of [self] and [recipients] for a sealed box; empty when neither is present. */
    fun keyOf(self: E2eSelf?, recipients: Collection<E2eRecipient>): String {
        val sender = self?.let { "${it.deviceId}:${E2eBytes.hex(it.key.publicKey)}" }.orEmpty()
        val to = keyOf(recipients)
        return if (sender.isEmpty() && to.isEmpty()) "" else "$sender|$to"
    }
}
