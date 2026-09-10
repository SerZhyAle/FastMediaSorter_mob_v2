package com.sza.fastmediasorter.wear.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.core.notification.NotificationIcons
import com.sza.fastmediasorter.wear.core.notification.WearNotificationIds
import com.sza.fastmediasorter.wear.data.broadcast.BroadcastDescriptorDto
import com.sza.fastmediasorter.wear.data.broadcast.BroadcastDescriptorSerializer
import com.sza.fastmediasorter.wear.data.broadcast.WearBroadcastIdentityStore
import com.sza.fastmediasorter.wear.data.wear.ListenAckSender
import com.sza.fastmediasorter.wear.domain.broadcast.WearBroadcastFailure
import com.sza.fastmediasorter.wear.domain.broadcast.WearBroadcastSessionState
import com.sza.fastmediasorter.wear.domain.broadcast.WearBroadcastSessionStateHolder
import com.sza.fastmediasorter.wear.domain.listen.ListenSessionState
import com.sza.fastmediasorter.wear.domain.listen.ListenSessionStateHolder
import com.sza.fastmediasorter.wear.domain.model.ListenRefusal
import com.sza.fastmediasorter.wear.domain.model.LiveAudioEndpoint
import com.sza.fastmediasorter.wear.domain.repository.BroadcastNetworkLease
import com.sza.fastmediasorter.wear.domain.repository.StreamNetworkHold
import com.sza.fastmediasorter.wear.service.helpers.LiveAudioLanServer
import com.sza.fastmediasorter.wear.service.helpers.VoiceRecordingSessionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/** The typed startForeground overload does not exist below API 29; there the type is simply absent. */
private const val FOREGROUND_SERVICE_TYPE_NONE = 0

/**
 * S1862 / S2161: owner of the microphone session (ADR-4).
 *
 * The screen starts and stops this service by intent and reads its state through an
 * application-scoped flow; it never binds, so a screen going dark cannot end a recording that is
 * still being spoken into. The note is stored the moment the session closes and before any transfer
 * is attempted (ADR-3) - speech is not reproducible, so a lost file is a lost note. S2161 publishes
 * the finished note into MediaStore.Audio before send.
 *
 * S2430: the session itself lives in [VoiceRecordingSessionManager]. What is left here is the part
 * that needs a `Service` - the platform callbacks, the foreground notification and the scope the
 * session runs in.
 */
@AndroidEntryPoint
class VoiceRecordingService : Service() {

    @Inject
    lateinit var sessionManager: VoiceRecordingSessionManager

    /**
     * S2550: the server lives here rather than on the confirmation screen, because the pipe it serves
     * belongs to THIS service's session manager - an unscoped injection at the screen would hand it a
     * different, unopened sink. The screen starts the service and reads [listenSession] instead.
     */
    @Inject
    lateinit var lanServer: LiveAudioLanServer

    @Inject
    lateinit var listenSession: ListenSessionStateHolder

    /**
     * S2550 ADR-2: the phone learns the address from here, where it is produced.
     *
     * Not from the confirmation screen, whose ViewModel dies with the window: the session is meant to
     * outlive that window, so an ack owed by it would be lost exactly in the case the design was
     * built for - the wrist dropping the moment the tap was given.
     */
    @Inject
    lateinit var listenAckSender: ListenAckSender

    /**
     * S2509: the owner-started broadcast's state, published from here for the same reason the listen
     * session is - the control screen is allowed to go dark while the broadcast continues.
     */
    @Inject
    lateinit var broadcastSession: WearBroadcastSessionStateHolder

    @Inject
    lateinit var descriptorSerializer: BroadcastDescriptorSerializer

    /** S2813: the port and the source id that must not change between two broadcasts of this watch. */
    @Inject
    lateinit var broadcastIdentity: WearBroadcastIdentityStore

    /**
     * S2509: the broadcast binds its server to the network this hands out, so "held" and "served on"
     * are the same interface rather than two guesses that usually agree.
     */
    @Inject
    lateinit var networkHold: StreamNetworkHold

    /** Owned for the broadcast's whole life and released on every stop and every failed-start path. */
    private var broadcastLease: BroadcastNetworkLease? = null

    /**
     * Main, deliberately: the session state the manager holds is also touched by `onStartCommand` and
     * `onDestroy`, and both of those are delivered on the main thread. One dispatcher for all of them
     * means the state needs no synchronisation; each blocking call inside hops to IO on its own.
     */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val sessionCallbacks = object : VoiceRecordingSessionManager.Callbacks {
        override fun onSendingStarted() = showSendingNotification()

        override fun onSessionFinished() = stopForegroundAndSelf()
    }

    override fun onCreate() {
        super.onCreate()
        sessionManager.attach(serviceScope, sessionCallbacks)
    }

    /** Never bound - see the class KDoc. A binder would put the session back under the screen. */
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * START_NOT_STICKY: a service the platform restarts arrives with a null intent and no microphone
     * session, so there is nothing to resume. Restarting it would only raise a recording
     * notification over a recorder that is not recording.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart()
            ACTION_START_LISTENING -> handleStartListening()
            ACTION_START_BROADCAST -> handleStartBroadcast()
            ACTION_STOP_BROADCAST -> serviceScope.launch { stopBroadcastSession() }
            ACTION_STOP -> serviceScope.launch { stopSession() }
            else -> Timber.w("VoiceRecordingService started with an unknown action: %s", intent?.action)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // The server goes down before the session does, because the session's own teardown closes the
        // pipe and the server must never be left holding a listener over a sink that is gone.
        lanServer.stop()
        // The ticker dies before the recorder, so nothing publishes a Recording state over a session
        // that no longer exists.
        serviceScope.cancel()
        sessionManager.release()
        // Before the listen state is cleared, and unconditionally: a held network outliving the
        // process that held it is the one leak the owner cannot see and cannot undo from the watch.
        releaseBroadcastLease()
        clearListenSessionUnlessFailed()
        clearBroadcastSessionUnlessFailed()
        super.onDestroy()
    }

    /**
     * A failed start stops the service on its own, so this teardown runs immediately after `Failed`
     * was published and would otherwise overwrite it with `Idle` - which is the state the screen
     * shows the request in, so the owner would be offered the microphone again with no word that it
     * had just refused to open.
     */
    private fun clearListenSessionUnlessFailed() {
        if (listenSession.state.value != ListenSessionState.Failed) {
            listenSession.publish(ListenSessionState.Idle)
        }
    }

    /** Same ordering rule as `onDestroy`: the server stops before the session closes the pipe. */
    private suspend fun stopSession() {
        lanServer.stop()
        sessionManager.stop()
    }

    private fun handleStart() {
        if (sessionManager.isSessionOpen) {
            Timber.i("Ignoring a start: the microphone session is already open")
            return
        }
        Timber.d("S2161: voice recording started")
        val notification = buildNotification(R.string.wear_voice_recorder_notification_title)
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundServiceType())
        serviceScope.launch {
            sessionManager.begin(VoiceRecordingSessionManager.Mode.VOICE_NOTE)
        }
    }

    /**
     * S2550 ADR-6: reachable only from `ListenRequestActivity`, a window the user opened by tapping
     * the request notification. That tap is what lifts both platform barriers at once, so no other
     * caller - and in particular not `WatchWearListenerService` - may start this path.
     *
     * The notification carries listening-specific wording rather than the voice-note title: the
     * shipped text would tell the owner a note is being recorded while the phone is listening live,
     * and strategic §3.2 makes an honest active-microphone indicator a hard constraint.
     */
    private fun handleStartListening() {
        if (sessionManager.isSessionOpen) {
            Timber.i("Ignoring a listen start: the microphone session is already open")
            return
        }
        val notification = buildNotification(R.string.wear_listen_notification_title)
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundServiceType())
        listenSession.publish(ListenSessionState.Starting)
        serviceScope.launch { openListeningSession() }
    }

    /**
     * The server starts only after the session opened the sink, which is the ordering Phase 02 hands
     * over: the server must attach after the pipe exists and detach before it is closed.
     */
    private suspend fun openListeningSession() {
        sessionManager.begin(VoiceRecordingSessionManager.Mode.LIVE_STREAM)
        if (!sessionManager.isSessionOpen) {
            // begin() already published the recorder's own error state and ended the session; all
            // that is left is to say the address will never arrive, so the phone stops waiting.
            listenSession.publish(ListenSessionState.Failed)
            listenAckSender.answerRefusal(ListenRefusal.CAPTURE_FAILED)
            return
        }
        // Off the main thread: binding the socket and walking the interface list to find the watch's
        // own LAN address are both blocking syscalls, and this coroutine runs on the service's Main
        // dispatcher. The accept loop the server launches picks its own IO dispatcher.
        val endpoint = withContext(Dispatchers.IO) {
            lanServer.start(serviceScope, sessionManager.liveSink)
        }
        listenSession.publish(ListenSessionState.Live(endpoint))
        // The address goes to the phone from here rather than from the confirmation screen: the
        // session outlives that screen by design (ADR-4), so a wrist dropped between the tap and the
        // bind would otherwise leave a live microphone with nobody told where to hear it.
        listenAckSender.answerServing(endpoint)
    }

    /**
     * S2509: the entrance the owner opens, with no phone involved at any point.
     *
     * It shares the one microphone with the two flows above and therefore refuses rather than queues:
     * the watch has a single recorder, and a second owner for it is a hardware conflict, not an
     * architectural one. The foreground type is the microphone type the manifest already declares -
     * this path adds a reason to hold the microphone, never a new way to hold it.
     */
    private fun handleStartBroadcast() {
        if (sessionManager.isSessionOpen) {
            Timber.i("Ignoring a broadcast start: the microphone session is already open")
            return
        }
        val notification = buildNotification(R.string.wear_broadcast_notification_title, withStopAction = true)
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundServiceType())
        broadcastSession.publish(WearBroadcastSessionState.Starting)
        serviceScope.launch { openBroadcastSession() }
    }

    /**
     * Ownership order, and it is the whole correctness of the phase: network, then capture, then the
     * server bound to that network, then the descriptor built from what the server actually bound.
     *
     * The network comes first because it is the only step that can refuse for a reason the owner can
     * act on - a watch off Wi-Fi cannot be reached at all - and opening the microphone before finding
     * that out would light the recording indicator for a session that was never going to serve anyone.
     */
    private suspend fun openBroadcastSession() {
        val lease = networkHold.acquireBroadcastNetwork()
        if (lease == null) {
            failBroadcast(WearBroadcastFailure.NO_USABLE_NETWORK)
            return
        }
        broadcastLease = lease
        sessionManager.begin(VoiceRecordingSessionManager.Mode.LIVE_STREAM)
        if (!sessionManager.isSessionOpen) {
            // begin() already published the recorder's own error state and ended the session.
            failBroadcast(WearBroadcastFailure.CAPTURE_FAILED)
            return
        }
        val preferredPort = broadcastIdentity.preferredPort()
        // Off the main thread: binding the socket is a blocking syscall and this coroutine runs on
        // the service's Main dispatcher. The accept loop the server launches picks its own IO one.
        val endpoint = withContext(Dispatchers.IO) {
            lanServer.start(serviceScope, sessionManager.liveSink, lease.address, preferredPort)
        }
        Timber.d("S2813: broadcast bound port ${endpoint.port}, preferred was $preferredPort")
        // Recorded after the bind, not before it: a refused preferred port falls back, and the next
        // session must start from the port that actually worked.
        broadcastIdentity.rememberPort(endpoint.port)
        broadcastSession.publish(liveStateOf(endpoint, broadcastIdentity.sourceId()))
    }

    /**
     * The descriptor is built once, here, and both published forms travel inside the state. The QR
     * screen and any later relay therefore encode the same address by construction - two encoders
     * reading the endpoint separately is how they would come to disagree.
     */
    private fun liveStateOf(endpoint: LiveAudioEndpoint, sourceId: String): WearBroadcastSessionState.Live {
        val descriptor = BroadcastDescriptorDto(
            url = endpoint.url,
            title = Build.MODEL,
            sourceId = sourceId
        )
        return WearBroadcastSessionState.Live(
            endpoint = endpoint,
            descriptorJson = descriptorSerializer.serialize(descriptor),
            descriptorQrPayload = descriptorSerializer.serializeCompressed(descriptor)
        )
    }

    /** Every failed-start path: the network goes back, the state says why, and the service ends. */
    private fun failBroadcast(reason: WearBroadcastFailure) {
        releaseBroadcastLease()
        broadcastSession.publish(WearBroadcastSessionState.Failed(reason))
        stopForegroundAndSelf()
    }

    /**
     * Same ordering rule as every other stop here, plus the network: the server goes down before the
     * session closes the pipe, and the network is given back only once nothing is left to serve on it.
     */
    private suspend fun stopBroadcastSession() {
        lanServer.stop()
        sessionManager.stop()
        releaseBroadcastLease()
        broadcastSession.publish(WearBroadcastSessionState.Idle)
    }

    private fun releaseBroadcastLease() {
        broadcastLease?.release()
        broadcastLease = null
    }

    /** A failed broadcast keeps its reason for [clearListenSessionUnlessFailed]'s reason. */
    private fun clearBroadcastSessionUnlessFailed() {
        if (broadcastSession.state.value !is WearBroadcastSessionState.Failed) {
            broadcastSession.publish(WearBroadcastSessionState.Idle)
        }
    }

    private fun stopForegroundAndSelf() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            FOREGROUND_SERVICE_TYPE_NONE
        }

    /**
     * S1862 phase 03: the ongoing notification stays up for the automatic transfer that follows a
     * recording, and the microphone is already released by then - leaving the recording title there
     * would tell the user the watch is still listening while it is only sending. A denied
     * POST_NOTIFICATIONS makes this a no-op; the transfer is unaffected either way.
     */
    private fun showSendingNotification() {
        if (!canPostNotification()) {
            return
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(
            NOTIFICATION_ID,
            buildNotification(R.string.wear_voice_recorder_notification_sending_title)
        )
    }

    private fun canPostNotification(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * S2509: [withStopAction] adds the one action a broadcast owes the owner.
     *
     * Strategic criterion 3 asks for a stop in one action, and criterion 5 lets the owner leave the
     * control screen while the microphone stays open - so the notification has to be a place the
     * broadcast can be ended from, not only a place it is announced from. The action names what it
     * ends rather than saying "stop", because this same channel also carries a voice note and a
     * paired-phone listening session.
     */
    private fun buildNotification(@StringRes titleRes: Int, withStopAction: Boolean = false): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.wear_voice_recorder_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(titleRes))
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setSilent(true)
        if (withStopAction) {
            builder.addAction(
                NotificationIcons.STATUS_BAR,
                getString(R.string.wear_broadcast_notification_stop),
                stopBroadcastPendingIntent()
            )
        }
        return builder.build()
    }

    /**
     * IMMUTABLE because nothing about this action is parameterised: it ends this service's broadcast
     * and there is no field an outside filler could usefully supply. API 31 requires the flag to be
     * stated either way.
     */
    private fun stopBroadcastPendingIntent(): PendingIntent = PendingIntent.getService(
        this,
        STOP_BROADCAST_REQUEST_CODE,
        stopBroadcastIntent(this),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        const val ACTION_START = "com.sza.fastmediasorter.wear.action.START_VOICE_RECORDING"
        const val ACTION_START_LISTENING = "com.sza.fastmediasorter.wear.action.START_LISTENING"
        const val ACTION_STOP = "com.sza.fastmediasorter.wear.action.STOP_VOICE_RECORDING"

        /** S2509: the owner's own broadcast, started from the wrist with no phone in the exchange. */
        const val ACTION_START_BROADCAST = "com.sza.fastmediasorter.wear.action.START_BROADCAST"

        /**
         * Its own stop rather than a reuse of [ACTION_STOP]: this one is also what the notification
         * action fires, and an action that ends "whatever is running" would be the wrong promise on a
         * button whose label names the broadcast.
         */
        const val ACTION_STOP_BROADCAST = "com.sza.fastmediasorter.wear.action.STOP_BROADCAST"

        /** Distinct from any other pending intent of this service; there is only the one. */
        private const val STOP_BROADCAST_REQUEST_CODE = 1

        /**
         * S1961: moved into [WearNotificationIds] once the watch gained a second notification.
         *
         * This used to be a literal justified by the watch shipping nothing else to collide with.
         * That stopped being true when the phone-initiated open began posting its own, and a number
         * repeated in two files is a collision nobody can see until both notifications are live.
         */
        private const val NOTIFICATION_ID = WearNotificationIds.VOICE_RECORDING
        private const val CHANNEL_ID = "wear_voice_recording"

        fun startIntent(context: Context): Intent =
            Intent(context, VoiceRecordingService::class.java).setAction(ACTION_START)

        fun stopIntent(context: Context): Intent =
            Intent(context, VoiceRecordingService::class.java).setAction(ACTION_STOP)

        /** S2550: called from `ListenRequestActivity` only - see `handleStartListening`. */
        fun startListeningIntent(context: Context): Intent =
            Intent(context, VoiceRecordingService::class.java).setAction(ACTION_START_LISTENING)

        /** S2509: called from the broadcast control screen, which is a window the owner opened. */
        fun startBroadcastIntent(context: Context): Intent =
            Intent(context, VoiceRecordingService::class.java).setAction(ACTION_START_BROADCAST)

        fun stopBroadcastIntent(context: Context): Intent =
            Intent(context, VoiceRecordingService::class.java).setAction(ACTION_STOP_BROADCAST)
    }
}
