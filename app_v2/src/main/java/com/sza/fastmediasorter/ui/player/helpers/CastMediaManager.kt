package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.mediarouter.app.MediaRouteChooserDialogFragment
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.CastMediaControlIntent
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.cast.LocalCastProxyServer
import com.sza.fastmediasorter.core.util.PermissionHelper
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Manages Chromecast media output from the player.
 *
 * Supported media types: IMAGE, GIF, AUDIO, VIDEO (local or small network/cloud files).
 * Placement: tapped via overflow menu item - same UX as "Search in YouTube Music".
 *
 * Architecture:
 * - [showCastDialog] opens the MediaRoute chooser so the user picks a device.
 * - On session start, [LocalCastProxyServer] is started and the current file is cast.
 * - For network/cloud sources, the file is downloaded to cacheDir first.
 * - VIDEO files > [MAX_VIDEO_CAST_BYTES] from network/cloud are refused with a Toast.
 */
class CastMediaManager(
    private val context: Context,
    private val lifecycleScope: CoroutineScope,
    private val networkFileManager: NetworkFileManager,
    private val mediaCapabilities: MediaCapabilities,
    private val onCastStateChanged: (isCasting: Boolean, deviceName: String?) -> Unit
) {

    companion object {
        private const val MAX_VIDEO_CAST_BYTES = 50L * 1024 * 1024   // 50 MB
        private const val DIALOG_TAG = "CastChooserDialog"
    }

    init {
    }

    // ── State ────────────────────────────────────────────────────────────────

    val isCasting: Boolean get() = _isCasting
    @Volatile private var _isCasting = false

    val castAvailableState = MutableStateFlow(false)

    private val proxyServer = LocalCastProxyServer(context)
    private var castContext: CastContext? = null
    private var currentSession: CastSession? = null
    private var downloadJob: Job? = null

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            Timber.d("CastMediaManager: session started id=$sessionId")
            currentSession = session
            _isCasting = true
            if (!proxyServer.isAlive) proxyServer.start()
            val deviceName = session.castDevice?.friendlyName
            onCastStateChanged(true, deviceName)
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            Timber.d("CastMediaManager: session ended error=$error")
            handleSessionEnd()
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            Timber.d("CastMediaManager: session suspended reason=$reason")
            handleSessionEnd()
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            Timber.d("CastMediaManager: session resumed")
            currentSession = session
            _isCasting = true
            if (!proxyServer.isAlive) proxyServer.start()
            onCastStateChanged(true, session.castDevice?.friendlyName)
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            Timber.w("CastMediaManager: session start failed error=$error")
            handleSessionEnd()
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            Timber.w("CastMediaManager: session resume failed error=$error")
        }

        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Call once after CastContext is available (i.e. after FastMediaSorterApp.onCreate).
     */
    fun init() {
        if (!mediaCapabilities.supportsCast) {
            Timber.i("CastMediaManager: cast not supported on this platform - init skipped")
            return
        }
        if (!PermissionHelper.hasLocalNetworkPermission(context)) {
            Timber.i("CastMediaManager: local network permission not granted - Cast init deferred")
            return
        }
        try {
            castContext = CastContext.getSharedInstance(context)
            castContext?.sessionManager?.addSessionManagerListener(
                sessionListener,
                CastSession::class.java
            )
            // Resume any pre-existing session (e.g. app re-launched while casting)
            currentSession = castContext?.sessionManager?.currentCastSession
            if (currentSession != null) {
                _isCasting = true
                if (!proxyServer.isAlive) proxyServer.start()
            }
            castAvailableState.value = true
            Timber.d("CastMediaManager: initialized, isCasting=$_isCasting")
        } catch (e: Exception) {
            Timber.w("CastMediaManager: Cast SDK not available - ${e.message}")
            castContext = null
            castAvailableState.value = false
        }
    }

    /** Unregister listener, stop proxy, cancel downloads. Call from onDestroy. */
    fun release() {
        downloadJob?.cancel()
        downloadJob = null
        try {
            castContext?.sessionManager?.removeSessionManagerListener(
                sessionListener,
                CastSession::class.java
            )
        } catch (e: Exception) {
            Timber.w("CastMediaManager: error removing listener - ${e.message}")
        }
        proxyServer.stop()
        _isCasting = false
        currentSession = null
        Timber.d("CastMediaManager: released")
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Returns true if the Cast SDK is available on this device (Google Play Services present).
     */
    val isCastAvailable: Boolean get() = castContext != null

    /**
     * Opens the Cast device picker dialog. Call when the user taps "Cast to Chromecast".
     */
    fun showCastDialog(activity: FragmentActivity) {
        if (!PermissionHelper.hasLocalNetworkPermission(activity)) {
            if (PermissionHelper.isLocalNetworkRuntimePermissionExpected()) {
                PermissionHelper.requestLocalNetworkPermission(activity)
            } else {
                PermissionHelper.routeToLocalNetworkSettings(activity)
            }
            return
        }
        // Lazy recovery: permission just granted but init() was deferred - re-initialize now
        if (castContext == null && mediaCapabilities.supportsCast) {
            init()
        }
        if (castContext == null) {
            Toast.makeText(activity, R.string.cast_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        if (!isWifiConnected()) {
            Toast.makeText(activity, R.string.cast_no_wifi, Toast.LENGTH_SHORT).show()
            return
        }
        val selector = MediaRouteSelector.Builder()
            .addControlCategory(
                CastMediaControlIntent.categoryForCast(
                    CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
                )
            )
            .build()
        val dialog = MediaRouteChooserDialogFragment()
        dialog.routeSelector = selector
        // Dismiss any existing instance before showing a new one
        val existing = activity.supportFragmentManager.findFragmentByTag(DIALOG_TAG)
        if (existing != null) {
            activity.supportFragmentManager.beginTransaction().remove(existing).commitAllowingStateLoss()
        }
        dialog.show(activity.supportFragmentManager, DIALOG_TAG)
    }

    /**
     * Cast [file] to the active Chromecast session.
     * If no session is active this is a no-op (the session listener will call sendMedia
     * when a session starts, if the user selects a device after tapping cast).
     */
    fun sendCurrentMedia(file: MediaFile) {
        if (!_isCasting || currentSession == null) {
            Timber.d("CastMediaManager: sendCurrentMedia called but not casting - ignoring")
            return
        }
        downloadJob?.cancel()
        downloadJob = lifecycleScope.launch {
            resolveAndSend(file)
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private suspend fun resolveAndSend(file: MediaFile) {
        val isLocalFile = !file.path.startsWith("smb://") &&
            !file.path.startsWith("sftp://") &&
            !file.path.startsWith("ftp://") &&
            !file.path.startsWith("cloud://")
        val localFile: File? = when {
            isLocalFile -> {
                // Local file: serve directly
                File(file.path)
            }
            file.type == MediaType.VIDEO && file.size > MAX_VIDEO_CAST_BYTES -> {
                // Too large: notify and bail
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.cast_video_too_large, Toast.LENGTH_LONG).show()
                }
                return
            }
            else -> {
                // Network/Cloud: download to temp cache
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.cast_preparing, Toast.LENGTH_SHORT).show()
                }
                downloadToTemp(file)
            }
        }

        if (localFile == null || !localFile.exists()) {
            Timber.w("CastMediaManager: could not resolve local file for ${file.name}")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.cast_error_file, Toast.LENGTH_SHORT).show()
            }
            return
        }

        proxyServer.serveFile(localFile)
        val castUrl = proxyServer.castUrl()
        // TODO(phase-06-deferred): apply panel single-eye crop to Cast output.
        // See PLAN/spec_panel-stereo-single-eye/PHASE_06__cast-feasibility.md.
        // Default Cast receiver decodes the original file natively; implementing the crop
        // requires either FFmpeg transcode in the proxy (heavy CPU/IO + doubled cache) or a
        // custom Cast receiver app. Deferred until either path is greenlit.
        Timber.d("CastMediaManager: casting ${file.name} via $castUrl")

        withContext(Dispatchers.Main) {
            loadMediaOnReceiver(file, castUrl)
        }
    }

    private fun loadMediaOnReceiver(file: MediaFile, url: String) {
        val session = currentSession ?: return
        val remoteClient = session.remoteMediaClient ?: return

        val streamType = when (file.type) {
            MediaType.IMAGE, MediaType.GIF -> MediaInfo.STREAM_TYPE_NONE
            else -> MediaInfo.STREAM_TYPE_BUFFERED
        }
        val metadata = MediaMetadata(
            when (file.type) {
                MediaType.IMAGE, MediaType.GIF -> MediaMetadata.MEDIA_TYPE_PHOTO
                MediaType.AUDIO -> MediaMetadata.MEDIA_TYPE_MUSIC_TRACK
                else -> MediaMetadata.MEDIA_TYPE_MOVIE
            }
        ).apply {
            putString(MediaMetadata.KEY_TITLE, file.name)
        }
        val mediaInfo = MediaInfo.Builder(url)
            .setStreamType(streamType)
            .setMetadata(metadata)
            .build()
        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .build()
        remoteClient.load(request)
            .setResultCallback { result ->
                if (!result.status.isSuccess) {
                    Timber.w("CastMediaManager: load failed - ${result.status.statusMessage}")
                    Toast.makeText(context, R.string.cast_error_load, Toast.LENGTH_SHORT).show()
                }
            }
    }

    /**
     * Downloads [file] content to a temporary local file and returns it.
     * Returns null on failure.
     *
     * Delegates to [NetworkFileManager.prepareFileForRead], which routes through
     * UnifiedFileCache and shares cached files with the player - re-casting the
     * same file in a session is therefore instant.
     */
    private suspend fun downloadToTemp(file: MediaFile): File? = withContext(Dispatchers.IO) {
        try {
            networkFileManager.prepareFileForRead(file)
        } catch (e: Exception) {
            Timber.e(e, "CastMediaManager: download failed for ${file.name}")
            null
        }
    }

    private fun handleSessionEnd() {
        _isCasting = false
        currentSession = null
        proxyServer.stop()
        onCastStateChanged(false, null)
    }

    fun isWifiConnected(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Exception) {
            false
        }
    }
}
