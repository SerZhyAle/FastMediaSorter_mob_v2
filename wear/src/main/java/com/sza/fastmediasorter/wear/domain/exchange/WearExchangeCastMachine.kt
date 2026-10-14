package com.sza.fastmediasorter.wear.domain.exchange

/** A `cast-offer` that arrived on the control stream of the entry named by [identity] (contract 7.8). */
data class WearExchangeCastOffer(
    val identity: String,
    val broadcastId: String,
    val title: String?,
)

/** What the watch does next for one offer; [WearExchangeCastMachine] decides, the coordinator acts. */
sealed interface WearExchangeCastAction {
    data class Answer(val offer: WearExchangeCastOffer, val accepted: Boolean, val reason: String?) :
        WearExchangeCastAction

    data class Ask(val offer: WearExchangeCastOffer) : WearExchangeCastAction
    data class Play(val offer: WearExchangeCastOffer) : WearExchangeCastAction
    data class Dismiss(val broadcastId: String) : WearExchangeCastAction
    data class StopPlaying(val broadcastId: String) : WearExchangeCastAction
}

/**
 * The receiver half of cast (contract DEVICE-EXCHANGE 7.8), mirrored from the phone's `ExchangeCastReceiverMachine`:
 * an offer is asked about by default and played at once only when [playAtOnce] says so; an offer the watch cannot
 * play is declined as `unsupported`; an answer is sent once per offer; a `cast-stop` withdraws the question or the
 * playback, and an unanswered question lapses with the server's own 60 s timeout. Not thread-safe - the coordinator
 * calls it from one coroutine.
 */
class WearExchangeCastMachine {

    private val pending = mutableMapOf<String, WearExchangeCastOffer>()
    private val playing = mutableSetOf<String>()

    fun onOffer(offer: WearExchangeCastOffer, playable: Boolean, playAtOnce: Boolean): List<WearExchangeCastAction> {
        val id = offer.broadcastId
        return when {
            !playable -> listOf(WearExchangeCastAction.Answer(offer, false, REASON_UNSUPPORTED))
            // A repeated offer for a broadcast already playing is answered the same way, nothing restarts.
            id in playing -> listOf(WearExchangeCastAction.Answer(offer, true, null))
            playAtOnce -> {
                playing += id
                pending -= id
                listOf(WearExchangeCastAction.Answer(offer, true, null), WearExchangeCastAction.Play(offer))
            }
            else -> {
                pending[id] = offer
                listOf(WearExchangeCastAction.Ask(offer))
            }
        }
    }

    /** The owner's choice on an asked offer; an offer withdrawn or lapsed meanwhile is ignored. */
    fun onUserAnswer(broadcastId: String, accepted: Boolean): List<WearExchangeCastAction> {
        val offer = pending.remove(broadcastId) ?: return listOf(WearExchangeCastAction.Dismiss(broadcastId))
        return if (accepted) {
            playing += broadcastId
            listOf(
                WearExchangeCastAction.Dismiss(broadcastId),
                WearExchangeCastAction.Answer(offer, true, null),
                WearExchangeCastAction.Play(offer),
            )
        } else {
            listOf(
                WearExchangeCastAction.Dismiss(broadcastId),
                WearExchangeCastAction.Answer(offer, false, REASON_DECLINED),
            )
        }
    }

    /** `cast-stop` from the caster, or the broadcast ended: nothing is asked or played any more. */
    fun onStopped(broadcastId: String): List<WearExchangeCastAction> {
        val actions = mutableListOf<WearExchangeCastAction>()
        if (pending.remove(broadcastId) != null) actions += WearExchangeCastAction.Dismiss(broadcastId)
        if (playing.remove(broadcastId)) actions += WearExchangeCastAction.StopPlaying(broadcastId)
        return actions
    }

    /** The question outlived the server's answer window; the caster already got `timeout`. */
    fun onExpired(broadcastId: String): List<WearExchangeCastAction> =
        if (pending.remove(broadcastId) != null) listOf(WearExchangeCastAction.Dismiss(broadcastId)) else emptyList()

    val isPlaying: Boolean
        get() = playing.isNotEmpty()

    companion object {
        const val REASON_DECLINED = "declined"
        const val REASON_UNSUPPORTED = "unsupported"
    }
}
