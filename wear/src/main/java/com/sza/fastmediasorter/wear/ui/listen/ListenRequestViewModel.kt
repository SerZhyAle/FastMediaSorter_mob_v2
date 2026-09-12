package com.sza.fastmediasorter.wear.ui.listen

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.sza.fastmediasorter.wear.data.wear.ListenAckSender
import com.sza.fastmediasorter.wear.domain.listen.ListenRequestRegistry
import com.sza.fastmediasorter.wear.domain.listen.ListenSessionState
import com.sza.fastmediasorter.wear.domain.listen.ListenSessionStateHolder
import com.sza.fastmediasorter.wear.domain.model.ListenRefusal
import com.sza.fastmediasorter.wear.domain.model.StreamChannelReason
import com.sza.fastmediasorter.wear.domain.model.StreamChannelVerdict
import com.sza.fastmediasorter.wear.domain.usecase.EvaluateStreamStartUseCase
import com.sza.fastmediasorter.wear.service.VoiceRecordingService
import com.sza.fastmediasorter.wear.service.helpers.ListenRequestNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import javax.inject.Inject

/**
 * S2550 ADR-6: the only place in the module that may start the microphone for a listening session.
 *
 * Both platform barriers strategic §6.1 measured are lifted by the tap that opened this screen, and
 * by nothing else - so `WatchWearListenerService` raises the notification and returns, and the start
 * happens here, while the window the user opened is in front.
 */
@HiltViewModel
class ListenRequestViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notifier: ListenRequestNotifier,
    private val ackSender: ListenAckSender,
    private val registry: ListenRequestRegistry,
    private val stateHolder: ListenSessionStateHolder,
    private val evaluateStreamStart: EvaluateStreamStartUseCase
) : ViewModel() {

    val state: StateFlow<ListenSessionState> = stateHolder.state

    /**
     * Honours the request: the notification is spent, and the service opens the microphone and binds
     * the server. The address it ends up serving arrives through [state], never as a return value -
     * the session outlives this screen by design (ADR-4).
     */
    fun confirm() {
        if (registry.peek() == null) {
            // The request expired while this window was open - the notification's timer runs on, and
            // the phone was already told nobody answered. Opening the microphone now would leave it
            // live with the only party who could hear it no longer listening, which is the covert
            // recording the Non-goals put outside the product.
            Timber.i("A listening request expired before it was confirmed; starting nothing")
            notifier.cancel()
            stateHolder.publish(ListenSessionState.Failed)
            return
        }
        startIfTheWatchCanServe()
    }

    /**
     * S2550 pillar E: the readiness that is checked is the watch's own readiness to serve.
     *
     * Asked here rather than when the command arrived, because answering earlier would let a phone
     * read this watch's network state without the owner ever being asked - the consent ADR-6 built
     * the whole flow around. By this point the tap has been given, so the only thing left to
     * establish is whether there is a LAN for the phone to reach.
     *
     * The Bluetooth case keeps its [StreamChannelReason.NOT_ON_WIFI] name over the wire, so the phone
     * can show the action that fixes it. Other refusal reasons remain [ListenRefusal.NO_NETWORK],
     * whose established wording also tells the owner to enable the watch Wi-Fi.
     */
    private fun startIfTheWatchCanServe() {
        val verdict = evaluateStreamStart.forServing()
        if (verdict is StreamChannelVerdict.Refuse) {
            Timber.i("The watch cannot serve a listening session: %s", verdict.reason)
            notifier.cancel()
            ackSender.answerRefusal(listenRefusalFor(verdict.reason))
            stateHolder.publish(ListenSessionState.Failed)
            return
        }
        Timber.i("The watch owner confirmed a listening request")
        notifier.cancel()
        ContextCompat.startForegroundService(
            context,
            VoiceRecordingService.startListeningIntent(context)
        )
    }

    /**
     * Refuses it. Nothing is started here and nothing may be: acceptance criterion 1a requires that a
     * command left unanswered produce no sound and leave no running service, and a decline arriving
     * after the request already expired must be that same nothing rather than a second start.
     */
    fun decline() {
        Timber.i("The watch owner declined a listening request")
        notifier.cancel()
        // Handed over, not awaited: this window finishes in the same frame, so an answer owed by
        // viewModelScope would be dropped by the very act that produced it. The sender owns a scope
        // the application owns.
        ackSender.answerRefusal(ListenRefusal.DECLINED)
    }

    /**
     * Ends a session this screen started; the service stops the server before closing the pipe.
     *
     * The phone is told, because it asked for this session and is still holding a socket to it - a
     * stop given on the watch that reached the phone only as a closed connection would leave it
     * guessing between "the owner ended it" and "the link dropped".
     */
    fun stopListening() {
        context.startService(VoiceRecordingService.stopIntent(context))
        ackSender.answerRefusal(ListenRefusal.STOPPED)
    }

    private fun listenRefusalFor(reason: StreamChannelReason): ListenRefusal = when (reason) {
        StreamChannelReason.NOT_ON_WIFI -> ListenRefusal.NOT_ON_WIFI
        else -> ListenRefusal.NO_NETWORK
    }
}
