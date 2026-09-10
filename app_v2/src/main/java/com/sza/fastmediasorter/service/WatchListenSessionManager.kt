package com.sza.fastmediasorter.service

import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.data.capture.ListenRecordingStore
import com.sza.fastmediasorter.domain.model.WearListenAckPayload
import com.sza.fastmediasorter.domain.model.WearListenRefusal
import com.sza.fastmediasorter.domain.model.streamUrl
import com.sza.fastmediasorter.domain.usecase.StartWatchListeningUseCase
import com.sza.fastmediasorter.domain.usecase.StopWatchListeningUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2550: the listening control's three appearances, and no fourth.
 *
 * What ended the last attempt rides on [Idle] rather than living in a state of its own, because a
 * refusal is not a fourth thing the control can be doing - it is idle with something to say, and the
 * start action must stay reachable in exactly that moment.
 */
sealed class WearListenState {

    /** @param messageRes why the last attempt ended, or null when nothing has been tried yet. */
    data class Idle(@param:StringRes val messageRes: Int? = null) : WearListenState()

    /** The command is with the watch and the owner has not yet tapped the request it raised. */
    data object Awaiting : WearListenState()

    /**
     * The watch's microphone is being played on this phone.
     *
     * @param recording S2881: whether this session was started with the record variant. It rides the
     * state rather than a flow of its own because every surface showing "listening" must read
     * "recording" from the same value - two sources would let the widget and the companion card
     * disagree about one session.
     */
    data class Listening(val recording: Boolean = false) : WearListenState()
}

/**
 * S2881: the one owner of a watch listening session, for the life of the process.
 *
 * Before this class the session lived in `WearSyncViewModel`, so the watch's answer was collected
 * only while the companion screen existed. A session started from a widget, a launcher shortcut or a
 * desktop tile had nobody to receive its answer and would have done nothing at all - strategic §7's
 * highest-probability risk. Nothing here needs a foreground service of its own: the command is an
 * ordinary Data Layer call, the answer arrives at [PhoneWearListenerService], which the system wakes
 * on its own, and playback reaches an already-bound media service through [WatchListenPlayback].
 *
 * Every surface reads [listenState] and calls [start] / [stop]; none of them keeps a session state
 * of its own.
 */
@Singleton
class WatchListenSessionManager @Inject constructor(
    private val playback: WatchListenPlayback,
    private val startWatchListeningUseCase: StartWatchListeningUseCase,
    private val stopWatchListeningUseCase: StopWatchListeningUseCase,
    private val listenRecordingStore: ListenRecordingStore,
    private val stateRenderer: WatchListenStateRenderer,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) {

    private val _listenState = MutableStateFlow<WearListenState>(WearListenState.Idle())
    val listenState: StateFlow<WearListenState> = _listenState.asStateFlow()

    /**
     * S2550: null except while a session of this process's is waiting for, or holding, an answer.
     *
     * `listenAckFlow` replays its last value, so a collector is handed the previous session's answer
     * at once. Matching this id is what tells that answer apart from the one being waited on -
     * without it a stale refusal would close a live session, and a stale address would be opened in
     * the player.
     */
    private var listenRequestId: String? = null

    /** S2881: held for the life of the session so the state can say whether it is being recorded. */
    private var recordRequested: Boolean = false

    private var listenTimeoutJob: Job? = null

    init {
        // S2550: one collector for the whole session rather than a one-shot await, because the watch
        // also answers after the session is running - a stop given on the wrist arrives here.
        applicationScope.launch {
            WearSyncEvents.listenAckFlow.collect { ack ->
                if (ack.requestId == listenRequestId) {
                    onListenAck(ack)
                }
            }
        }
        // S2881 pillar E: every state change lands on the surfaces that show it, through the
        // renderer port - the owner takes no Context, the surfaces take no owner. The first
        // emission is immediate, so a widget pinned mid-session is corrected as soon as the
        // process exists to hold the session.
        applicationScope.launch {
            listenState.collect { state -> stateRenderer.render(state) }
        }
    }

    /**
     * S2550 §3.1: the phone initiates, and this is where it does.
     *
     * The command opens no microphone. ADR-6 puts the owner's tap on the watch between this and any
     * capture, so what leaves here is a request the watch raises as a notification and nothing else.
     *
     * @param record S2881: start the session with the recording variant. The flag never reaches the
     * watch - the wire contract is unchanged and recording is entirely a phone-side concern.
     */
    fun start(record: Boolean = false) {
        Timber.d("S2881: start requested, record=%b, state=%s", record, _listenState.value::class.java.simpleName)
        if (_listenState.value !is WearListenState.Idle) {
            return
        }
        val requestId = UUID.randomUUID().toString()
        listenRequestId = requestId
        recordRequested = record
        _listenState.value = WearListenState.Awaiting
        playback.prepareSession()
        startListenTimeout(requestId)
        applicationScope.launch {
            startWatchListeningUseCase(requestId).onFailure { e ->
                Timber.i(e, "Could not ask the watch to listen")
                endListenSession(R.string.wear_listen_send_failed)
            }
        }
    }

    /** The stop action, from whichever surface offers it. */
    fun stop() {
        endListenSession(messageRes = null)
    }

    /**
     * The watch answers every outcome of its own accord, its own two-minute expiry included, so this
     * bound is only for the case where nothing on the other side is alive to answer at all. It sits
     * above that expiry rather than competing with it: firing first would report "no answer" for a
     * request the owner is still looking at.
     */
    private fun startListenTimeout(requestId: String) {
        listenTimeoutJob?.cancel()
        listenTimeoutJob = applicationScope.launch {
            delay(LISTEN_ANSWER_TIMEOUT_MS)
            if (listenRequestId == requestId && _listenState.value is WearListenState.Awaiting) {
                Timber.w("The watch did not answer a listen command in $LISTEN_ANSWER_TIMEOUT_MS ms")
                endListenSession(R.string.wear_listen_no_answer)
            }
        }
    }

    private fun onListenAck(ack: WearListenAckPayload) {
        Timber.d("S2881: watch ack arrived, requestId matches=%b", ack.requestId == listenRequestId)
        listenTimeoutJob?.cancel()
        val refusal = ack.refusal
        if (refusal == null) {
            Timber.i("The watch is serving a listening session")
            playListenStream(ack.streamUrl())
        } else {
            Timber.i("The watch is not serving a listening session: %s", refusal)
            endListenSession(messageFor(refusal))
        }
    }

    private fun playListenStream(url: String) {
        // S2881: the recording is armed before playback, not when Listening is reached: the data
        // source that carries the bytes is opened inside this start and asks for its sink there.
        if (recordRequested) {
            listenRecordingStore.begin(url)
        }
        playback.start(
            url = url,
            onPlaying = { _listenState.value = WearListenState.Listening(recordRequested) },
            onDropped = { onListenStreamDropped() },
        )
    }

    /**
     * S2550 §6.6, the explicit form: the media connection dropped mid-session.
     *
     * It delegates to [endListenSession], which stops playback, sends the stop over the control
     * channel that outlived the media one, and only then returns the control to idle - carrying the
     * one instruction that helps, that the watch left Wi-Fi.
     */
    private fun onListenStreamDropped() {
        if (_listenState.value !is WearListenState.Listening) {
            return
        }
        Timber.i("The watch's audio stream dropped; ending the listening session")
        endListenSession(R.string.wear_listen_wifi_lost)
    }

    /**
     * The one exit from a listening session, in the order acceptance criterion 2 needs: playback
     * stops first so nothing is still pulling on the watch's server, the stop command goes out so the
     * watch extinguishes its microphone indicator, and only then does the control return to idle.
     *
     * @param messageRes what to say about why it ended, or null when the owner ended it themselves.
     */
    private fun endListenSession(@StringRes messageRes: Int?) {
        Timber.d("S2881: session ends, message=%s", messageRes)
        val requestId = listenRequestId
        listenRequestId = null
        recordRequested = false
        listenTimeoutJob?.cancel()
        // S2881 §5.2: the recording is closed first, so the file is whole before the source that
        // filled it is torn down - a stop from the wrist, a dropped stream and the owner's own stop
        // all arrive here, and all three leave a playable file behind.
        val recording = listenRecordingStore.endCapture()
        playback.stop()
        if (requestId != null) {
            applicationScope.launch {
                stopWatchListeningUseCase(requestId).onFailure { e ->
                    Timber.i(e, "Could not tell the watch its listening session ended")
                }
            }
        }
        _listenState.value = WearListenState.Idle(messageRes)
        if (recording != null) {
            saveRecording(recording)
        }
    }

    /**
     * S2881: the save outlives the session, because a network destination can take a while.
     *
     * Its verdict replaces the message the session ended with, and only while the control is still
     * idle: a session started in the meantime owns the control, and its own outcome is the one the
     * owner is waiting on.
     */
    private fun saveRecording(recording: File) {
        applicationScope.launch {
            val outcome = listenRecordingStore.finish(recording)
            if (_listenState.value is WearListenState.Idle) {
                _listenState.value = WearListenState.Idle(messageFor(outcome))
            }
        }
    }

    /** S2881: what the owner is told about the file, in the dictaphone's own three outcomes. */
    @StringRes
    private fun messageFor(outcome: ListenRecordingStore.Outcome): Int = when (outcome) {
        ListenRecordingStore.Outcome.SAVED -> R.string.wear_listen_record_saved
        ListenRecordingStore.Outcome.SAVED_TO_FALLBACK -> R.string.wear_listen_record_saved_fallback
        ListenRecordingStore.Outcome.FAILED -> R.string.wear_listen_record_failed
    }

    /** Each value exists because it needs different words; a shared one would be a silent screen. */
    @StringRes
    private fun messageFor(refusal: WearListenRefusal): Int = when (refusal) {
        WearListenRefusal.NOT_ASKED -> R.string.wear_listen_refusal_not_asked
        WearListenRefusal.DECLINED -> R.string.wear_listen_refusal_declined
        WearListenRefusal.EXPIRED -> R.string.wear_listen_refusal_expired
        WearListenRefusal.CAPTURE_FAILED -> R.string.wear_listen_refusal_capture_failed
        WearListenRefusal.NO_NETWORK -> R.string.wear_listen_refusal_no_network
        WearListenRefusal.NOT_ON_WIFI -> R.string.wear_listen_refusal_no_network
        WearListenRefusal.STOPPED -> R.string.wear_listen_refusal_stopped
        WearListenRefusal.BUSY -> R.string.wear_listen_refusal_busy
        WearListenRefusal.UNKNOWN -> R.string.wear_listen_refusal_unknown
    }

    private companion object {
        // S2550: above the watch's own two-minute request expiry, which answers on its own - this
        // covers only a watch that cannot answer at all.
        const val LISTEN_ANSWER_TIMEOUT_MS = 150_000L
    }
}
