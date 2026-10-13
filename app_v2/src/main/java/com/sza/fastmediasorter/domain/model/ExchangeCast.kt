package com.sza.fastmediasorter.domain.model

/** One `{mode, transport}` an endpoint of a broadcast offers (contract DEVICE-EXCHANGE 6.1, item E). */
data class ExchangeEndpointPair(val mode: String, val transport: String)

/** A device of the account a broadcast may be cast to (7.8), with its presence and whether it can play it. */
data class ExchangeCastTarget(
    val deviceId: String,
    val name: String,
    val online: Boolean,
)

/** A `cast-offer` received on this device's control stream (7.8). */
data class ExchangeCastOffer(
    val broadcastId: String,
    val fromDeviceId: String?,
    val record: SftpRendezvousBroadcast,
) {
    override fun toString(): String = "ExchangeCastOffer(mode=${record.mode})"
}

/** A `cast-result` forwarded to this broadcaster (7.8, item R). */
data class ExchangeCastResult(
    val broadcastId: String,
    val receiverDeviceId: String,
    val accepted: Boolean,
    val reason: String?,
) {
    override fun toString(): String = "ExchangeCastResult(accepted=$accepted, reason=$reason)"
}

/** The pure rules of cast: what this phone declares as a receiver, and whom a broadcaster may offer a cast. */
object ExchangeCastRules {

    const val REASON_UNAVAILABLE = "unavailable"
    const val REASON_DECLINED = "declined"
    const val REASON_TIMEOUT = "timeout"
    const val REASON_UNSUPPORTED = "unsupported"

    const val TRANSPORT_HTTP = "HTTP"
    const val TRANSPORT_RTSP = "RTSP"
    const val TRANSPORT_RELAY = "RELAY"
    const val TRANSPORT_TUNNEL = "TUNNEL"

    private const val ROLE_RECEIVER = "receiver"

    /**
     * The phone plays every `LIVE-BROADCAST` mode on every endpoint transport it implements, so its `plays` is the
     * full product of the two lists; it is still written out, so a broadcaster reading only `plays` matches it.
     */
    fun phoneReceiver(modes: List<String>): SftpRendezvousReceiver {
        val transports = listOf(TRANSPORT_HTTP, TRANSPORT_RTSP, TRANSPORT_RELAY, TRANSPORT_TUNNEL)
        return SftpRendezvousReceiver(
            modes = modes,
            transports = transports,
            plays = modes.flatMap { mode -> transports.map { SftpRendezvousPlayPair(mode, it) } },
        )
    }

    /**
     * Item E: a receiver covers a broadcast when one of its endpoints is a pair it plays - `plays` when the
     * receiver lists it, else its `modes` and `transports` as two independent lists.
     */
    fun covers(receiver: SftpRendezvousReceiver?, endpoints: List<ExchangeEndpointPair>): Boolean {
        receiver ?: return false
        val plays = receiver.plays
        return endpoints.any { endpoint ->
            if (plays != null) {
                plays.any { it.mode.equals(endpoint.mode, true) && it.transport.equals(endpoint.transport, true) }
            } else {
                receiver.modes.any { it.equals(endpoint.mode, true) } &&
                    receiver.transports.any { it.equals(endpoint.transport, true) }
            }
        }
    }

    /**
     * The receivers a broadcaster offers (amendment E): every other device of the account that declared the
     * receiver role and covers one of [endpoints]; online first, then by name. A device that cannot play the
     * broadcast is not offered at all, so the server's `unsupported` stays the exception.
     */
    fun castTargets(
        devices: List<SftpRendezvousDevice>,
        ownDeviceId: String,
        endpoints: List<ExchangeEndpointPair>,
    ): List<ExchangeCastTarget> = devices
        .asSequence()
        .filter { it.deviceId != ownDeviceId }
        .filter { ROLE_RECEIVER in it.roles || it.receiver != null }
        .filter { covers(it.receiver, endpoints) }
        .map { ExchangeCastTarget(it.deviceId, it.deviceName, it.isOnline) }
        .sortedWith(compareBy({ !it.online }, { it.name.lowercase() }))
        .toList()
}

/** What the receiver does next for one offer; [ExchangeCastReceiverMachine] decides, the coordinator acts. */
sealed interface ExchangeCastAction {
    data class Answer(val broadcastId: String, val accepted: Boolean, val reason: String?) : ExchangeCastAction
    data class Ask(val offer: ExchangeCastOffer) : ExchangeCastAction
    data class Play(val offer: ExchangeCastOffer) : ExchangeCastAction
    data class Dismiss(val broadcastId: String) : ExchangeCastAction
}

/**
 * The receiver half of cast (7.8): an offer is asked about by default and played at once only when the user chose
 * that; an offer this build cannot play is declined as `unsupported`; an answer is sent once per offer; a
 * `cast-stop` or the end of the broadcast withdraws the question or the playback. Not thread-safe - the
 * coordinator calls it from one coroutine.
 */
class ExchangeCastReceiverMachine {

    private val pending = mutableMapOf<String, ExchangeCastOffer>()
    private val playing = mutableSetOf<String>()

    fun onOffer(offer: ExchangeCastOffer, playable: Boolean, playAtOnce: Boolean): List<ExchangeCastAction> {
        val id = offer.broadcastId
        return when {
            !playable -> listOf(ExchangeCastAction.Answer(id, false, ExchangeCastRules.REASON_UNSUPPORTED))
            // A repeated offer for a broadcast already playing is answered the same way, nothing restarts.
            id in playing -> listOf(ExchangeCastAction.Answer(id, true, null))
            playAtOnce -> {
                playing += id
                pending -= id
                listOf(ExchangeCastAction.Answer(id, true, null), ExchangeCastAction.Play(offer))
            }
            else -> {
                pending[id] = offer
                listOf(ExchangeCastAction.Ask(offer))
            }
        }
    }

    /** The user's choice on an asked offer; an offer withdrawn meanwhile is ignored. */
    fun onUserAnswer(broadcastId: String, accepted: Boolean): List<ExchangeCastAction> {
        val offer = pending.remove(broadcastId) ?: return listOf(ExchangeCastAction.Dismiss(broadcastId))
        return if (accepted) {
            playing += broadcastId
            listOf(
                ExchangeCastAction.Dismiss(broadcastId),
                ExchangeCastAction.Answer(broadcastId, true, null),
                ExchangeCastAction.Play(offer),
            )
        } else {
            listOf(
                ExchangeCastAction.Dismiss(broadcastId),
                ExchangeCastAction.Answer(broadcastId, false, ExchangeCastRules.REASON_DECLINED),
            )
        }
    }

    /** `cast-stop` from the caster, or the broadcast left the directory: nothing is asked or played any more. */
    fun onStopped(broadcastId: String): List<ExchangeCastAction> {
        val wasAsked = pending.remove(broadcastId) != null
        val wasPlaying = playing.remove(broadcastId)
        val known = wasAsked || wasPlaying
        return if (known) listOf(ExchangeCastAction.Dismiss(broadcastId)) else emptyList()
    }

    fun offer(broadcastId: String): ExchangeCastOffer? = pending[broadcastId]
}
